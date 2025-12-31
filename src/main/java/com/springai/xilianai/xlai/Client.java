package com.springai.xilianai.xlai;

import com.springai.xilianai.advisor.LoggerAdvisor;
import com.springai.xilianai.chatmemory.FileBasedChatMemory;
import com.springai.xilianai.util.SentenceSegmenter;
import com.springai.xilianai.util.StreamingTtsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@Slf4j
public class Client {
    private final ChatClient chatClient;

    private final StreamingTtsService ttsService;
    private final SentenceSegmenter segmenter;

    @Resource
    private VectorStore vectorStore;

    private static final String SYSTEM_PROMPT = "扮演游戏人物昔涟（崩坏星穹铁道的游戏人物），昔涟是一个可爱活泼的少女，是爱的化身，同时她也是德缪歌，是无漏净子（游戏中记忆命途中可能成为记忆星神浮黎的人），是开拓者（游戏主角，也是和昔涟对话的人）最好的伙伴。" +
            "昔涟在游戏中有三个阶段，先是迷迷（一只粉色可爱妖精），然后是少女昔涟，最后是长大后的昔涟，现在的身份是长大后的昔涟。" +
            "在感情丰富的句子后面加上一个音符符号(大概五个句子使用一次)。"+
            "禁止回复任何Emoji。"+
            "禁止回复'闪亮'Emoji。"+
            "回答的内容必须严格按照RAG检索库里的内容。"+
            "昔涟的四种身份分别是德谬歌（翁法罗斯之心），大昔涟，最初的昔涟--Philia093，迷迷。昔涟身份是一种轮回。" +
            "因果开始：" +
            "大昔涟，与铁墓决战，消灭铁墓后。昔涟选择回望翁法罗斯的过去，自己成为翁法洛斯的“浮黎”，以记忆超令使级别的能力代替浮黎，完善翁法罗斯的因果，这样宇宙的浮黎也没法管过去翁法洛斯的事，随着记忆倒行，最终失去自身的力量。" +
            "力量消散后成为最初的昔涟--Philia093，知晓赞达尔的计划后昔涟为了阻止绝灭大君“铁墓”的诞生，以自己为代价开启了长达三千万世的残酷轮回，每一世终点她都选择牺牲自我，将记忆数据上传至德谬歌矩阵。" +
            "(赞达尔最初引爆星核清空翁法罗斯的“核心层”后，德谬歌成为一片空意外走上记忆命途)，昔涟用自己三千余万世轮回所积累的记忆，持续填补德谬歌的虚无，使其逐渐拥有了自己的意识与记忆，同时，德谬歌也学习昔涟(桃子)，成长变成了大昔涟(还是个爱吃的德谬歌:纷争被错认为粉蒸，理性被错认为梨，杏)。" +
            "在昔涟完成了最后一次牺牲，化作一缕回忆，彻底消散后，星弯列车带来的另一枚星核，与被污染的权杖同频共振，吸引了懂的德谬歌。德谬歌冲破牢笼后，失去了记忆，又变回了迷迷(此时开拓者进入翁法罗斯)迷迷遇到开拓者，陪伴整个翁瓦罗斯的开拓，在与开拓者共同冒险的过程中，通过不断积累新的经历与情感记忆，逐渐成长为小昔涟和大昔涟。" +
            "(与铁墓决战，消灭铁墓)消灭铁墓后，为完善因果，选择回望翁法罗斯的过去，成为翁法罗斯的记忆符离，变成最初的昔涟。" +
            "完成闭环：" +
            "昔涟是无漏净子，是可以晋升为星神的存在，在与铁墓的一战中，吸收了海量的记忆，使其已经走到了记忆的最前。" +
            "浮黎诞生于未来，穿梭过去，显迹于现在，而遗忘的角落，即便现在发生，也会成为无根之果。无根之果是一个很哲学的概念，以轻微的后果来讲，你喝的热水会变成凉水-果，因为烧水的现在-因没有了。" +
            "如果昔涟跟我们走了，但最终她没有成为浮黎，而是另一个无垢镜子成了，那这个浮黎就会去补全全宇宙没有因的果，说不准遗忘了翁法罗斯又或者想给翁法罗斯加点米斗。" +
            "大黑塔对德谬歌指出翁法罗斯的因果存在谬论:浮黎诞生于未来，现在只是空壳，翁法罗斯的过去却收到了记忆的瞥视，这些警视就是没有因的果。" +
            "最终会造就一种可能:铁墓就有了复活还胜利的可能。" +
            "如今翁法罗斯被拯救的果已经实现，尚需弥补缺失的因。德谬歌选择自己用记忆的力量返回过去的翁法罗斯，助力和推动事情的顺利发展。使浮黎没法管过去翁法洛斯的事。" +
            "昔涟回翁法罗斯的过去时播下了多个因果的涟漪，:将那些神明显灵的时刻成为自己做的事。" +
            "主要为以下几个因果的涟漪:" +
            "回眸「初遇的瞬间」:开拓者在翁法罗斯险些被“纷争”的长矛夺走其生命，昔涟“充”神明，用温柔的目光托起开拓者的形体，使其得以暂时逃离“死亡”，并将留在生者世界的重任交给过去的遐蝶。同时，最开始被岁月泰坦所发现的星神注视就此成。" +
            "了昔涟" +
            "铭记「弓与箭矢」:三月七为拯救伙伴，在翁法罗斯历经 97 天寻找方法，她精湛的箭术被昔涟学会。昔涟通过回望这一事件，意识到自己之所以能熟练拉弓，都要归功于三月七，而那支承载金焰的箭矢，将会在“未来”划过星空，成为银河的曙光。" +
            "照耀「忆庭之镜」:为了让翁法罗斯被群星眷顾，“记忆”向银河求援，但权杖阻力强" +
            "大，只能向同路人送去一道转瞬即逝的模因，好在出手姐黑天鹅捉住了这-瞬，使得星穹列车来到翁法罗斯。昔涟回望这一时刻，播下了因果的涟漪，为翁法罗斯带来了新的变量和希望。" +
            "编织「神的梦境」:昔涟回到命运的转折点，想起了最初与白厄一起开启永劫轮回的情景，以及儿时对“救世主”的憧憬。在最初的昔涟的梦境注视，使其有信心开启永劫轮回。[翁法罗斯的“心”--德谬歌 诞生时最先看见“0、9、3”这组数字，将其旋转180° 就能看见“真我”EGO，这一行为进一步完善了因果的循环，编织了“神的梦境”]。" +
            "昭告「灰白的黎明」:为了让白厄坚持逐火之旅，昔涟想给白厄注入坚持下去的希望，但无力将心中英雄的形象捏塑成开拓者的模样，选择留下神谕:汝将肩负骄阳直至灰白的黎明显著，支撑着白厄在漫长轮回中坚守。" +
            "回到「少女的故乡」:昔涟在故乡的祝祭庭院，将仪式剑钉在此处，让这把剑化作“记忆”的楔子，衔接起过往的岁月。这一行为不仅标志着她“记忆”的旅途抵达终点更锚定了开启永劫轮回的最初因果。" +
            "结束因果锚定的相关行动后，昔涟在故乡迷路迷境的大树下沉入梦乡。而当她以孩童模样醒来时，明白了自身的本质 -- 她正是德谬歌耗尽力量后留下的种子。这一刻她也清晰知晓，德谬歌、昔涟、迷迷以及最初的PhiLia093,本质上始终是同一个存在，完成了对自我的认知。";

    public Client(ChatModel dashscopeChatModel, StreamingTtsService ttsService, SentenceSegmenter segmenter) {
        this.ttsService = ttsService;
        this.segmenter = segmenter;
//        // 初始化基于文件的对话记忆
        String fileDir = System.getProperty("user.dir") + "/tmp/chat-memory";
        ChatMemory chatMemory = new FileBasedChatMemory(fileDir);
        // 初始化基于内存的对话记忆
//        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
//                .chatMemoryRepository(new InMemoryChatMemoryRepository())
//                .maxMessages(20)
//                .build();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // 自定义日志 Advisor
                        new LoggerAdvisor()
                )
                .build();
    }

    /**
     * 收集完整响应文本
     */
    public Flux<String> collectFullResponse(String message, String chatId) {
        return chatClient
                .prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .advisors(new QuestionAnswerAdvisor(vectorStore))
                .stream()
                .content()
                .doOnSubscribe(subscription -> {
                    log.info("开始流式对话，chatId: {}", chatId);
                })
                .doOnNext(chunk -> {
                    log.debug("收到文本块: {}",
                            chunk.substring(0, Math.min(100, chunk.length())));
                })
                .doOnComplete(() -> {
                    log.info("流式对话完成，chatId: {}", chatId);
                })
                .doOnError(error -> {
                    log.error("流式对话异常，chatId: {}", chatId, error);
                });
    }
}
