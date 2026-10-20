package com.springai.xilianai.chatmemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import lombok.extern.slf4j.Slf4j;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于文件持久化的对话记忆
 */
@Slf4j
public class FileBasedChatMemory implements ChatMemory {

    private final String BASE_DIR;

    /**
     * Kryo 不是线程安全的：DefaultClassResolver 内部复用了 StringBuilder 缓存类名，
     * 并发 readName 会读到彼此交错的字节，表现为 "Unable to find class: xxx" 这类诡异异常。
     * Spring MVC 请求线程与 Reactor 线程会并发访问对话记忆，因此按线程各持一个实例。
     */
    private static final ThreadLocal<Kryo> KRYO = ThreadLocal.withInitial(FileBasedChatMemory::newKryo);

    private static Kryo newKryo() {
        Kryo kryo = new Kryo();
        kryo.setRegistrationRequired(false);
        // 设置实例化策略
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
        return kryo;
    }

    // 构造对象时，指定文件保存目录
    public FileBasedChatMemory(String dir) {
        this.BASE_DIR = dir;
        File baseDir = new File(dir);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        List<Message> conversationMessages = getOrCreateConversation(conversationId);
        conversationMessages.addAll(messages);
        saveConversation(conversationId, conversationMessages);
    }

    @Override
    public List<Message> get(String conversationId) {
        return getOrCreateConversation(conversationId);
    }

    @Override
    public void clear(String conversationId) {
        File file = getConversationFile(conversationId);
        if (file.exists()) {
            file.delete();
        }
    }

    private List<Message> getOrCreateConversation(String conversationId) {
        File file = getConversationFile(conversationId);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try (Input input = new Input(new FileInputStream(file))) {
            @SuppressWarnings("unchecked")
            List<Message> messages = KRYO.get().readObject(input, ArrayList.class);
            return messages == null ? new ArrayList<>() : messages;
        } catch (IOException | KryoException e) {
            // 文件损坏（例如旧版本并发写坏）：挪走并当作空会话，避免整个会话一直 500
            log.error("读取对话记忆失败，文件可能已损坏，已隔离：{}", file.getAbsolutePath(), e);
            quarantine(file);
            return new ArrayList<>();
        }
    }

    private void saveConversation(String conversationId, List<Message> messages) {
        File target = getConversationFile(conversationId);
        File temp = new File(BASE_DIR, conversationId + ".kryo.tmp");
        try (OutputStream stream = new BufferedOutputStream(new FileOutputStream(temp));
             Output output = new Output(stream)) {
            KRYO.get().writeObject(output, messages);
            output.flush();
        } catch (IOException | KryoException e) {
            log.error("写入对话记忆失败：{}", target.getAbsolutePath(), e);
            return;
        }
        // 先写临时文件再原子替换：避免读到被截断的半个文件
        try {
            Files.move(temp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("替换对话记忆文件失败：{}", target.getAbsolutePath(), e);
        }
    }

    private void quarantine(File file) {
        File broken = new File(BASE_DIR, file.getName() + ".corrupt-" + System.currentTimeMillis());
        if (file.renameTo(broken)) {
            log.warn("已将损坏的会话文件移动到：{}", broken.getAbsolutePath());
        }
    }

    private File getConversationFile(String conversationId) {
        return new File(BASE_DIR, conversationId + ".kryo");
    }

    public List<String> findKryoFiles(String directoryPath) {
        List<String> kryoFiles = new ArrayList<>();
        File dir = new File(directoryPath);

        if (dir.exists() && dir.isDirectory()) {
            // 获取所有.kryo文件
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".kryo"));

            if (files != null) {
                for (File file : files) {
                    String fileName = file.getName();
                    // 去除.kryo后缀
                    String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
                    kryoFiles.add(nameWithoutExt);
                }
            }
        } else {
            System.out.println("目录不存在或不是有效目录: " + directoryPath);
        }
        return kryoFiles;
    }
}
