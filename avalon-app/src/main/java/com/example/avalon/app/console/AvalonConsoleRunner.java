package com.example.avalon.app.console;

import com.example.avalon.agent.harness.AgentHarnessType;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.api.dto.CreateGameRequest;
import com.example.avalon.api.dto.GameAuditEntryResponse;
import com.example.avalon.api.dto.GameEventEntryResponse;
import com.example.avalon.api.dto.GameStateResponse;
import com.example.avalon.api.dto.GameSummaryResponse;
import com.example.avalon.api.dto.ModelProfileProbeRequest;
import com.example.avalon.api.dto.ModelProfileProbeResponse;
import com.example.avalon.api.dto.ModelProfileResponse;
import com.example.avalon.api.dto.PlayerPrivateViewResponse;
import com.example.avalon.api.service.GameApplicationService;
import com.example.avalon.api.service.AdminGameInspectionService;
import com.example.avalon.api.service.AdminInspectionCapability;
import com.example.avalon.api.service.LocalConsoleAdminAccess;
import com.example.avalon.api.service.ModelProfileCatalogService;
import com.example.avalon.api.service.ModelProfileProbeService;
import com.example.avalon.api.service.SeedGenerator;
import com.example.avalon.config.model.AvalonConfigRegistry;
import com.example.avalon.core.setup.model.SetupTemplate;
import com.example.avalon.core.player.controller.PlayerActionGenerationException;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

@Component
@ConditionalOnProperty(prefix = "avalon.console", name = "enabled", havingValue = "true")
public class AvalonConsoleRunner implements ApplicationRunner {
    private static final int DEFAULT_PLAYER_COUNT = 5;
    private static final int MIN_PLAYER_COUNT = 5;
    private static final int MAX_PLAYER_COUNT = 10;

    private final GameApplicationService gameApplicationService;
    private final AdminGameInspectionService adminGameInspectionService;
    private final AdminInspectionCapability adminInspectionCapability;
    private final ModelProfileCatalogService modelProfileCatalogService;
    private final ModelProfileProbeService modelProfileProbeService;
    private final AvalonConfigRegistry configRegistry;
    private final SeedGenerator seedGenerator;
    private final ConsoleTranscriptPrinter printer;
    private final ConsoleDecisionReportBuilder decisionReportBuilder;
    private final ConsolePlaybackSettings playbackSettings;
    private final ConsolePlaybackDelayer playbackDelayer;
    private final ObjectProvider<ConsoleModelStreamReporter> modelStreamReporter;
    private final ConfigurableApplicationContext applicationContext;
    private final Path reportOutputDir;
    private final ConsoleGameSession session = new ConsoleGameSession();

    public AvalonConsoleRunner(GameApplicationService gameApplicationService,
                               AdminGameInspectionService adminGameInspectionService,
                               LocalConsoleAdminAccess localConsoleAdminAccess,
                               ModelProfileCatalogService modelProfileCatalogService,
                               ModelProfileProbeService modelProfileProbeService,
                               AvalonConfigRegistry configRegistry,
                               SeedGenerator seedGenerator,
                               ConsoleTranscriptPrinter printer,
                               ConsoleDecisionReportBuilder decisionReportBuilder,
                               ConsolePlaybackSettings playbackSettings,
                               ConsolePlaybackDelayer playbackDelayer,
                               ObjectProvider<ConsoleModelStreamReporter> modelStreamReporter,
                               @Value("${avalon.console.report.output-dir:target/reports/avalon}") String reportOutputDir,
                               ConfigurableApplicationContext applicationContext) {
        this.gameApplicationService = gameApplicationService;
        this.adminGameInspectionService = adminGameInspectionService;
        this.adminInspectionCapability = localConsoleAdminAccess.capability();
        this.modelProfileCatalogService = modelProfileCatalogService;
        this.modelProfileProbeService = modelProfileProbeService;
        this.configRegistry = configRegistry;
        this.seedGenerator = seedGenerator;
        this.printer = printer;
        this.decisionReportBuilder = decisionReportBuilder;
        this.playbackSettings = playbackSettings;
        this.playbackDelayer = playbackDelayer;
        this.modelStreamReporter = modelStreamReporter;
        this.reportOutputDir = Path.of(reportOutputDir);
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        System.out.println(printer.banner());
        System.out.println(printer.helpText());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            commandLoop(reader);
        } finally {
            applicationContext.close();
        }
    }

    private void commandLoop(BufferedReader reader) throws IOException {
        while (true) {
            System.out.print(prompt());
            System.out.flush();
            String line = reader.readLine();
            if (line == null) {
                System.out.println();
                System.out.println("输入流已关闭，控制台退出。");
                return;
            }

            String commandLine = line.trim();
            if (commandLine.isEmpty()) {
                continue;
            }

            try {
                if (!dispatch(commandLine, reader)) {
                    return;
                }
            } catch (Exception exception) {
                System.out.println("命令执行失败：" + commandFailureMessage(exception));
                org.slf4j.LoggerFactory.getLogger(AvalonConsoleRunner.class)
                        .error("Console command failed: {}", commandLine, exception);
            }
        }
    }

    private String commandFailureMessage(Exception exception) {
        if (exception instanceof PlayerActionGenerationException generationException) {
            Object validation = generationException.rawMetadata().get("validation");
            if (validation instanceof Map<?, ?> validationMap) {
                Object detail = validationMap.get("errorMessage");
                if (detail != null && !String.valueOf(detail).isBlank()) {
                    return generationException.getMessage() + "；具体原因：" + detail;
                }
            }
            Throwable cause = generationException.getCause();
            if (cause != null && cause.getMessage() != null && !cause.getMessage().isBlank()) {
                return generationException.getMessage() + "；具体原因：" + cause.getMessage();
            }
        }
        Throwable root = exception;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            message = exception.getMessage();
        }
        return exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : "：" + message);
    }

    private boolean dispatch(String commandLine, BufferedReader reader) throws IOException {
        String[] parts = commandLine.split("\\s+");
        String command = parts[0].toLowerCase(Locale.ROOT);
        return switch (command) {
            case "new" -> {
                createNewGame(reader);
                yield true;
            }
            case "use" -> {
                useExisting(parts);
                yield true;
            }
            case "config" -> {
                ensureActiveGame();
                System.out.println(printer.formatConfig(session));
                yield true;
            }
            case "start" -> {
                startActiveGame();
                yield true;
            }
            case "step" -> {
                stepActiveGame();
                yield true;
            }
            case "run" -> {
                runActiveGame();
                yield true;
            }
            case "report" -> {
                printDecisionReportCommand();
                yield true;
            }
            case "state" -> {
                printState();
                yield true;
            }
            case "players" -> {
                printAllPlayerViews();
                yield true;
            }
            case "player" -> {
                printPlayerView(parts);
                yield true;
            }
            case "events" -> {
                printAllEvents();
                yield true;
            }
            case "replay" -> {
                printReplay();
                yield true;
            }
            case "audit" -> {
                printAudit();
                yield true;
            }
            case "probe-model" -> {
                probeModel(parts);
                yield true;
            }
            case "log-level" -> {
                if (parts.length < 2) {
                    throw new IllegalArgumentException("用法：log-level <info|debug|trace>");
                }
                session.setLogLevel(ConsoleLogLevel.parse(parts[1]));
                ConsoleModelStreamReporter reporter = modelStreamReporter.getIfAvailable();
                if (reporter != null) {
                    reporter.setLogLevel(session.logLevel());
                }
                System.out.println("实时日志级别=" + session.logLevel());
                yield true;
            }
            case "help" -> {
                System.out.println(printer.helpText());
                yield true;
            }
            case "exit", "quit" -> {
                System.out.println("控制台关闭。");
                yield false;
            }
            default -> {
                System.out.println("未知命令：" + command + "。请使用 `help` 查看帮助。");
                yield true;
            }
        };
    }

    private String prompt() {
        return session.hasActiveGame()
                ? "avalon[" + session.gameId() + "]> "
                : "avalon> ";
    }

    private void createNewGame(BufferedReader reader) throws IOException {
        CreateGameRequest request = buildCreateRequest(reader);
        GameSummaryResponse summary = gameApplicationService.createGame(request);
        session.activateNewGame(summary.getGameId(), request);
        session.resolveRandomPoolModelNames(modelProfileCatalogService.listAll());

        System.out.println("已创建新游戏 " + summary.getGameId() + "，状态=" + summary.getStatus());
        System.out.println(printer.formatConfig(session));
        printNewEvents();
        printStateIfDebug();
        System.out.println("输入 `start` 开始游戏，或输入 `run` 直接慢速播放整局。");
    }

    private CreateGameRequest buildCreateRequest(BufferedReader reader) throws IOException {
        CreateGameRequest request = new CreateGameRequest();
        int playerCount = promptPlayerCount(reader);
        ClassicSetupSelection selection = classicSetupSelection(playerCount);
        request.setRuleSetId(selection.ruleSetId());
        request.setSetupTemplateId(selection.setupTemplateId());
        request.setSeed(promptOptionalLong(reader, "随机种子 [留空自动生成]："));
        SetupTemplate setupTemplate = configRegistry.requireSetupTemplate(selection.setupTemplateId());

        SeatPreset preset = promptPreset(reader);
        CreateRequestDraft draft = switch (preset) {
            case SCRIPTED -> new CreateRequestDraft(scriptedSeats(playerCount), null);
            case NOOP_LLM -> new CreateRequestDraft(noopLlmSeats(playerCount), null);
            case SEAT_BOUND_MODEL_POOL -> new CreateRequestDraft(
                    modelPoolLlmSeats(playerCount),
                    promptSeatBindingSelection(reader, seatNumbers(playerCount))
            );
            case ROLE_BOUND_MODEL_POOL -> new CreateRequestDraft(
                    modelPoolLlmSeats(playerCount),
                    promptRoleBindingSelection(reader, setupTemplate)
            );
            case RANDOM_MODEL_POOL -> {
                List<SeatInput> seats = modelPoolLlmSeats(playerCount);
                configureRandomPoolHarness(reader, seats, request.getSeed());
                yield new CreateRequestDraft(seats, promptRandomPoolSelection(reader));
            }
            case CUSTOM -> promptCustomSeats(reader, setupTemplate, playerCount);
        };

        List<CreateGameRequest.PlayerSlotRequest> players = new ArrayList<>();
        for (SeatInput seatInput : draft.seatInputs()) {
            CreateGameRequest.PlayerSlotRequest player = new CreateGameRequest.PlayerSlotRequest();
            player.setSeatNo(seatInput.seatNo());
            player.setDisplayName(seatInput.displayName());
            player.setControllerType(seatInput.controllerType());
            player.setAgentConfig(seatInput.agentConfig());
            players.add(player);
        }
        request.setPlayers(players);
        request.setLlmSelection(draft.llmSelection());
        return request;
    }

    private int promptPlayerCount(BufferedReader reader) throws IOException {
        while (true) {
            String raw = promptString(reader, "参与人数 [" + DEFAULT_PLAYER_COUNT + "]：", String.valueOf(DEFAULT_PLAYER_COUNT));
            try {
                int value = Integer.parseInt(raw);
                if (value < MIN_PLAYER_COUNT || value > MAX_PLAYER_COUNT) {
                    System.out.println("只支持 " + MIN_PLAYER_COUNT + " 到 " + MAX_PLAYER_COUNT + " 人局。");
                    continue;
                }
                return value;
            } catch (NumberFormatException ignored) {
                System.out.println("请输入合法的人数。");
            }
        }
    }

    private SeatPreset promptPreset(BufferedReader reader) throws IOException {
        while (true) {
            String raw = promptString(reader,
                    "席位预设 [custom/scripted/noop/seat/role/random]（默认 random）：",
                    "random").toLowerCase(Locale.ROOT);
            switch (raw) {
                case "custom", "c" -> {
                    return SeatPreset.CUSTOM;
                }
                case "scripted", "s" -> {
                    return SeatPreset.SCRIPTED;
                }
                case "noop", "n", "llm", "llm-noop" -> {
                    return SeatPreset.NOOP_LLM;
                }
                case "seat", "seat-binding", "player", "p", "catalog-seat" -> {
                    return SeatPreset.SEAT_BOUND_MODEL_POOL;
                }
                case "role", "r", "catalog-role" -> {
                    return SeatPreset.ROLE_BOUND_MODEL_POOL;
                }
                case "random", "rand", "catalog-random" -> {
                    return SeatPreset.RANDOM_MODEL_POOL;
                }
                case "openai", "o" -> System.out.println("控制台不再逐局录入原始 OpenAI 参数。请改用 model profile，并选择 `seat`、`role` 或 `random`。");
                default -> System.out.println("无效预设。可选 custom、scripted、noop、seat、role、random。");
            }
        }
    }

    private List<SeatInput> scriptedSeats(int playerCount) {
        List<SeatInput> seats = new ArrayList<>();
        for (int seatNo = 1; seatNo <= playerCount; seatNo++) {
            seats.add(new SeatInput(seatNo, "P" + seatNo, "SCRIPTED", null));
        }
        return seats;
    }

    private List<SeatInput> noopLlmSeats(int playerCount) {
        List<SeatInput> seats = new ArrayList<>();
        for (int seatNo = 1; seatNo <= playerCount; seatNo++) {
            seats.add(new SeatInput(seatNo, "P" + seatNo, "LLM", defaultNoopLlmConfig()));
        }
        return seats;
    }

    private List<SeatInput> modelPoolLlmSeats(int playerCount) {
        List<SeatInput> seats = new ArrayList<>();
        for (int seatNo = 1; seatNo <= playerCount; seatNo++) {
            seats.add(new SeatInput(seatNo, "P" + seatNo, "LLM", defaultModelPoolLlmConfig()));
        }
        return seats;
    }

    private CreateRequestDraft promptCustomSeats(BufferedReader reader, SetupTemplate setupTemplate, int playerCount) throws IOException {
        List<SeatInput> seats = new ArrayList<>();
        List<SeatMode> modes = new ArrayList<>();
        List<AgentHarnessType> harnessTypes = new ArrayList<>();
        List<String> displayNames = new ArrayList<>();
        List<Integer> modelPoolSeatNos = new ArrayList<>();
        int noopSeatCount = 0;

        for (int seatNo = 1; seatNo <= playerCount; seatNo++) {
            String defaultName = "P" + seatNo;
            String displayName = promptString(reader, seatNo + "号位显示名 [" + defaultName + "]：", defaultName);
            SeatMode mode = promptSeatMode(reader, seatNo);
            displayNames.add(displayName);
            modes.add(mode);
            harnessTypes.add(mode == SeatMode.NOOP_LLM
                    ? AgentHarnessType.DEFAULT
                    : AgentHarnessType.TOOL_CALLING);
            if (mode == SeatMode.MODEL_POOL_LLM) {
                modelPoolSeatNos.add(seatNo);
            }
            if (mode == SeatMode.NOOP_LLM) {
                noopSeatCount++;
            }
        }

        if (!modelPoolSeatNos.isEmpty() && noopSeatCount > 0) {
            throw new IllegalArgumentException("控制台暂不支持在同一局里混用 noop LLM 和模型池 LLM。请二选一，或改用 server 模式。");
        }

        CreateGameRequest.LlmSelectionRequest llmSelection = !modelPoolSeatNos.isEmpty()
                ? promptSelectionMode(reader, setupTemplate, modelPoolSeatNos)
                : null;
        for (int index = 0; index < playerCount; index++) {
            int seatNo = index + 1;
            SeatMode mode = modes.get(index);
            String displayName = displayNames.get(index);
            PlayerAgentConfig agentConfig = switch (mode) {
                case SCRIPTED -> null;
                case NOOP_LLM -> defaultNoopLlmConfig();
                case MODEL_POOL_LLM -> defaultModelPoolLlmConfig();
            };
            if (agentConfig != null) {
                agentConfig.setHarnessType(harnessTypes.get(index));
            }
            String controllerType = mode == SeatMode.SCRIPTED ? "SCRIPTED" : "LLM";
            seats.add(new SeatInput(seatNo, displayName, controllerType, agentConfig));
        }
        return new CreateRequestDraft(seats, llmSelection);
    }

    private SeatMode promptSeatMode(BufferedReader reader, int seatNo) throws IOException {
        while (true) {
            String raw = promptString(reader,
                    seatNo + "号位控制方式 [scripted/noop/model]（默认 scripted）：",
                    "scripted").toLowerCase(Locale.ROOT);
            switch (raw) {
                case "scripted", "s" -> {
                    return SeatMode.SCRIPTED;
                }
                case "noop", "n", "llm", "llm-noop" -> {
                    return SeatMode.NOOP_LLM;
                }
                case "model", "m", "pool", "catalog" -> {
                    return SeatMode.MODEL_POOL_LLM;
                }
                case "openai", "o" -> System.out.println("控制台已移除逐局 OpenAI 参数录入。请使用 `model` 引用 model profile。");
                default -> System.out.println("无效控制方式。可选 scripted、noop、model。");
            }
        }
    }

    private AgentHarnessType promptHarnessType(BufferedReader reader, int seatNo) throws IOException {
        return promptHarnessType(reader, seatNo + "号位");
    }

    private AgentHarnessType promptHarnessType(BufferedReader reader, String target) throws IOException {
        while (true) {
            String raw = promptString(reader,
                    target + " Agent Harness [tool/default]（默认 tool）：",
                    "tool").toLowerCase(Locale.ROOT);
            switch (raw) {
                case "default", "d" -> {
                    return AgentHarnessType.DEFAULT;
                }
                case "tool", "tool-calling", "t" -> {
                    return AgentHarnessType.TOOL_CALLING;
                }
                default -> System.out.println("无效 Harness。可选 default 或 tool。");
            }
        }
    }

    private void configureRandomPoolHarness(BufferedReader reader,
                                            List<SeatInput> seats,
                                            long seed) throws IOException {
        String raw = promptString(reader,
                "全体 Agent Harness [random/default/tool]（默认 default）：",
                "default").toLowerCase(Locale.ROOT);
        while (!raw.equals("random") && !raw.equals("rand")
                && !raw.equals("default") && !raw.equals("d")
                && !raw.equals("tool") && !raw.equals("tool-calling") && !raw.equals("t")) {
            System.out.println("无效 Harness。可选 random、default 或 tool。");
            raw = promptString(reader,
                    "全体 Agent Harness [random/default/tool]（默认 default）：",
                    "default").toLowerCase(Locale.ROOT);
        }
        if (raw.equals("random") || raw.equals("rand")) {
            Random random = new Random(seed);
            for (int index = 0; index < seats.size(); index++) {
                seats.set(index, withHarness(seats.get(index),
                        random.nextBoolean() ? AgentHarnessType.DEFAULT : AgentHarnessType.TOOL_CALLING));
            }
            return;
        }
        AgentHarnessType harnessType = raw.equals("default") || raw.equals("d")
                ? AgentHarnessType.DEFAULT
                : AgentHarnessType.TOOL_CALLING;
        for (int index = 0; index < seats.size(); index++) {
            seats.set(index, withHarness(seats.get(index), harnessType));
        }
    }

    private SeatInput withHarness(SeatInput seat, AgentHarnessType harnessType) {
        seat.agentConfig().setHarnessType(harnessType);
        return seat;
    }

    private PlayerAgentConfig defaultNoopLlmConfig() {
        PlayerAgentConfig config = new PlayerAgentConfig();
        config.setHarnessType(AgentHarnessType.DEFAULT);
        config.setOutputSchemaVersion("v1");
        config.setCognition(Map.of("evidenceThreshold", 0.55, "beliefRevisionRate", 0.35));
        config.setCommunication(Map.of("challengeRate", 0.45, "commitmentStrength", 0.60));
        config.setDeception(Map.of("riskBudget", 0.25, "coverStoryPersistence", 0.70));
        return config;
    }

    private PlayerAgentConfig defaultModelPoolLlmConfig() {
        PlayerAgentConfig config = new PlayerAgentConfig();
        config.setOutputSchemaVersion("v1");
        config.setCognition(Map.of("evidenceThreshold", 0.70, "beliefRevisionRate", 0.25));
        config.setCommunication(Map.of("challengeRate", 0.70, "commitmentStrength", 0.45));
        config.setDeception(Map.of("riskBudget", 0.45, "coverStoryPersistence", 0.85));
        return config;
    }

    private CreateGameRequest.LlmSelectionRequest promptSelectionMode(BufferedReader reader,
                                                                      SetupTemplate setupTemplate,
                                                                      List<Integer> llmSeatNos) throws IOException {
        while (true) {
            String raw = promptString(reader, "LLM 选模方式 [seat/role/random]（默认 random）：", "random").toLowerCase(Locale.ROOT);
            switch (raw) {
                case "seat", "seat-binding", "player", "p" -> {
                    return promptSeatBindingSelection(reader, llmSeatNos);
                }
                case "role", "r" -> {
                    return promptRoleBindingSelection(reader, setupTemplate);
                }
                case "random", "rand" -> {
                    return promptRandomPoolSelection(reader);
                }
                default -> System.out.println("无效选模方式。可选 seat、role 或 random。");
            }
        }
    }

    private CreateGameRequest.LlmSelectionRequest promptSeatBindingSelection(BufferedReader reader,
                                                                             List<Integer> llmSeatNos) throws IOException {
        List<ModelProfileResponse> profiles = availableModelProfiles();
        printModelProfiles(profiles);
        List<String> defaultModelIds = defaultBindingModelIds(profiles, llmSeatNos.size());
        printDefaultSeatBindings(llmSeatNos, defaultModelIds);
        CreateGameRequest.LlmSelectionRequest request = new CreateGameRequest.LlmSelectionRequest();
        request.setMode("SEAT_BINDING");
        for (int index = 0; index < llmSeatNos.size(); index++) {
            Integer seatNo = llmSeatNos.get(index);
            String defaultModelId = defaultModelIds.get(index);
            String modelId = promptModelId(reader,
                    profiles,
                    seatNo + "号位使用的 modelId [" + defaultModelId + "]：",
                    defaultModelId);
            request.getSeatBindings().put(seatNo, modelId);
            request.getSeatHarnessBindings().put(seatNo, promptHarnessType(reader, seatNo).name());
        }
        return request;
    }

    private CreateGameRequest.LlmSelectionRequest promptRoleBindingSelection(BufferedReader reader,
                                                                             SetupTemplate setupTemplate) throws IOException {
        List<ModelProfileResponse> profiles = availableModelProfiles();
        printModelProfiles(profiles);
        List<String> roleIds = distinctRoleIds(setupTemplate);
        List<String> defaultModelIds = defaultBindingModelIds(profiles, roleIds.size());
        printDefaultRoleBindings(roleIds, defaultModelIds);
        CreateGameRequest.LlmSelectionRequest request = new CreateGameRequest.LlmSelectionRequest();
        request.setMode("ROLE_BINDING");
        for (int index = 0; index < roleIds.size(); index++) {
            String roleId = roleIds.get(index);
            String defaultModelId = defaultModelIds.get(index);
            String modelId = promptModelId(reader,
                    profiles,
                    "身份 " + roleId + " 使用的 modelId [" + defaultModelId + "]：",
                    defaultModelId);
            request.getRoleBindings().put(roleId, modelId);
            request.getRoleHarnessBindings().put(roleId, promptHarnessType(reader, "身份 " + roleId).name());
        }
        return request;
    }

    private CreateGameRequest.LlmSelectionRequest promptRandomPoolSelection(BufferedReader reader) {
        List<ModelProfileResponse> profiles = availableModelProfiles();
        printModelProfiles(profiles);
        System.out.println("将按候选 model profile 顺序轮询分配；席位多于模型时从第一个模型继续循环。");
        CreateGameRequest.LlmSelectionRequest request = new CreateGameRequest.LlmSelectionRequest();
        request.setMode("RANDOM_POOL");
        return request;
    }

    private List<ModelProfileResponse> availableModelProfiles() {
        List<ModelProfileResponse> profiles = modelProfileCatalogService.listAll().stream()
                .filter(ModelProfileResponse::isEnabled)
                .toList();
        if (profiles.isEmpty()) {
            throw new IllegalStateException("当前没有启用的 model profile。请先新增托管 profile 或启用静态配置。");
        }
        return profiles;
    }

    private void printModelProfiles(List<ModelProfileResponse> profiles) {
        System.out.println("可用 model profile：");
        for (ModelProfileResponse profile : profiles) {
            System.out.println("  - " + profile.getModelId()
                    + " | " + profile.getSource()
                    + " | " + profile.getProvider()
                    + " | " + profile.getModelName());
        }
    }

    private List<String> defaultBindingModelIds(List<ModelProfileResponse> profiles, int requiredCount) {
        List<String> defaultModelIds = new ArrayList<>();
        for (int index = 0; index < requiredCount; index++) {
            defaultModelIds.add(profiles.get(index % profiles.size()).getModelId());
        }
        return defaultModelIds;
    }

    private void printDefaultSeatBindings(List<Integer> seatNos, List<String> defaultModelIds) {
        System.out.println("默认座位绑定：");
        for (int index = 0; index < seatNos.size(); index++) {
            System.out.println("  - " + seatNos.get(index) + "号位 -> " + defaultModelIds.get(index));
        }
        System.out.println("直接回车即可接受每个座位的默认值。");
    }

    private void printDefaultRoleBindings(List<String> roleIds, List<String> defaultModelIds) {
        System.out.println("默认身份绑定：");
        for (int index = 0; index < roleIds.size(); index++) {
            System.out.println("  - " + roleIds.get(index) + " -> " + defaultModelIds.get(index));
        }
        System.out.println("直接回车即可接受每个身份的默认值。");
    }

    private String promptModelId(BufferedReader reader,
                                 List<ModelProfileResponse> profiles,
                                 String prompt) throws IOException {
        return promptModelId(reader, profiles, prompt, "");
    }

    private String promptModelId(BufferedReader reader,
                                 List<ModelProfileResponse> profiles,
                                 String prompt,
                                 String defaultModelId) throws IOException {
        while (true) {
            String modelId = promptString(reader, prompt, defaultModelId);
            if (profiles.stream().anyMatch(profile -> Objects.equals(profile.getModelId(), modelId))) {
                return modelId;
            }
            System.out.println("未知 modelId，请从上面的可用 model profile 列表中选择。");
        }
    }

    private void useExisting(String[] parts) {
        if (parts.length < 2 || parts[1].isBlank()) {
            throw new IllegalArgumentException("用法：use <gameId>");
        }
        session.useExistingGame(parts[1]);
        GameStateResponse state = gameApplicationService.getState(parts[1]);
        syncCurrentLeader(state);
        if (!"WAITING".equals(state.getStatus())) {
            refreshPlayerRoles();
        }
        System.out.println("已绑定到现有游戏 " + parts[1] + "。");
        printStateIfDebug();
    }

    private void startActiveGame() {
        String gameId = ensureActiveGame();
        GameSummaryResponse summary = gameApplicationService.startGame(gameId);
        syncCurrentLeader(gameApplicationService.getState(gameId));
        refreshPlayerRoles();
        System.out.println("游戏已启动，状态=" + summary.getStatus());
        printNewEvents();
        printStateIfDebug();
    }

    private void refreshPlayerRoles() {
        for (int seatNo = 1; seatNo <= activePlayerCount(); seatNo++) {
            String playerId = "P" + seatNo;
            PlayerPrivateViewResponse view = adminGameInspectionService.getPlayerView(
                    session.gameId(), playerId, adminInspectionCapability);
            session.rememberRole(playerId, view.getRoleSummary());
        }
    }

    private void stepActiveGame() {
        String gameId = ensureActiveGame();
        GameStateResponse before = gameApplicationService.getState(gameId);
        if ("WAITING".equals(before.getStatus())) {
            System.out.println("游戏尚未开始。请先执行 `start` 或直接执行 `run`。");
            return;
        }
        syncCurrentLeader(before);
        resetModelStreamActionStarts();
        gameApplicationService.stepGame(gameId);
        printNewEvents();
        printNewAudits();
        GameStateResponse after = gameApplicationService.getState(gameId);
        printStateIfDebug(after);
        printDecisionReportIfTerminal(after, before.getStatus());
    }

    private void runActiveGame() {
        String gameId = ensureActiveGame();
        GameStateResponse state = gameApplicationService.getState(gameId);
        if ("WAITING".equals(state.getStatus())) {
            startActiveGame();
            state = gameApplicationService.getState(gameId);
        }

        int safety = 500;
        while ("RUNNING".equals(state.getStatus()) && safety-- > 0) {
            syncCurrentLeader(state);
            announceTurn(state);
            playbackDelayer.sleep(playbackSettings.enabled() ? playbackSettings.actorLeadInMs() : 0L);
            resetModelStreamActionStarts();
            gameApplicationService.stepGame(gameId);
            printNewEvents();
            printNewAudits();
            state = gameApplicationService.getState(gameId);
            printStateIfDebug(state);
            playbackDelayer.sleep(playbackSettings.enabled() ? playbackSettings.afterStepMs() : 0L);
        }

        if (safety <= 0) {
            throw new IllegalStateException("运行步数超过安全上限 500");
        }
        if ("ENDED".equals(state.getStatus()) || "PAUSED".equals(state.getStatus())) {
            printDecisionReport(state);
        }
    }

    private void syncCurrentLeader(GameStateResponse state) {
        session.updateCurrentLeader(state.getPublicState() == null ? null : state.getPublicState().get("leaderSeat"));
    }

    private void announceTurn(GameStateResponse state) {
        if (!playbackSettings.enabled()) {
            return;
        }
        synchronized (ConsoleModelStreamReporter.outputLock()) {
            System.out.println(printer.formatTurnLeadIn(state, session));
        }
    }

    private void resetModelStreamActionStarts() {
        ConsoleModelStreamReporter reporter = modelStreamReporter.getIfAvailable();
        if (reporter != null) {
            reporter.resetStartedActions();
        }
    }

    private void printState() {
        String gameId = ensureActiveGame();
        GameStateResponse state = gameApplicationService.getState(gameId);
        System.out.println(printer.formatState(state, session));
    }

    private void printStateIfDebug() {
        if (session.logLevel() != ConsoleLogLevel.INFO) {
            printState();
        }
    }

    private void printStateIfDebug(GameStateResponse state) {
        if (session.logLevel() != ConsoleLogLevel.INFO) {
            System.out.println(printer.formatState(state, session));
        }
    }

    private void printAllPlayerViews() {
        ensureActiveGame();
        for (int seatNo = 1; seatNo <= activePlayerCount(); seatNo++) {
            String playerId = "P" + seatNo;
            PlayerPrivateViewResponse view = adminGameInspectionService.getPlayerView(
                    session.gameId(), playerId, adminInspectionCapability);
            System.out.println(printer.formatPlayerView(playerId, view, session));
        }
    }

    private void printPlayerView(String[] parts) {
        ensureActiveGame();
        if (parts.length < 2 || parts[1].isBlank()) {
            throw new IllegalArgumentException("用法：player <playerId>");
        }
        String playerId = parts[1].toUpperCase(Locale.ROOT);
        PlayerPrivateViewResponse view = adminGameInspectionService.getPlayerView(
                session.gameId(), playerId, adminInspectionCapability);
        System.out.println(printer.formatPlayerView(playerId, view, session));
    }

    private void printAllEvents() {
        ensureActiveGame();
        List<GameEventEntryResponse> events = adminGameInspectionService.getEvents(
                session.gameId(), adminInspectionCapability);
        if (events.isEmpty()) {
            System.out.println("当前还没有事件记录。");
            return;
        }
        for (GameEventEntryResponse event : events) {
            System.out.println(printer.formatEvent(event, session));
        }
    }

    private void printReplay() {
        ensureActiveGame();
        List<GameEventEntryResponse> replay = gameApplicationService.getReplay(session.gameId());
        if (replay.isEmpty()) {
            System.out.println("当前没有可用回放。");
            return;
        }
        for (GameEventEntryResponse step : replay) {
            System.out.println(printer.formatReplayStep(step, session));
        }
    }

    private void printAudit() {
        ensureActiveGame();
        List<GameAuditEntryResponse> auditEntries = adminGameInspectionService.getAudit(
                session.gameId(), adminInspectionCapability);
        if (auditEntries.isEmpty()) {
            System.out.println("当前还没有审计记录。");
            return;
        }
        for (GameAuditEntryResponse entry : auditEntries) {
            System.out.println(printer.formatAuditEntry(entry));
        }
    }

    private void probeModel(String[] parts) {
        if (parts.length < 2 || parts[1].isBlank()) {
            throw new IllegalArgumentException("用法：probe-model <modelId> [connectivity|structured|all]");
        }
        ModelProfileProbeRequest request = new ModelProfileProbeRequest();
        if (parts.length >= 3 && !parts[2].isBlank()) {
            request.setChecks(switch (parts[2].trim().toLowerCase(Locale.ROOT)) {
                case "all" -> List.of("CONNECTIVITY", "STRUCTURED_JSON");
                case "connectivity", "connect" -> List.of("CONNECTIVITY");
                case "structured", "json" -> List.of("STRUCTURED_JSON");
                default -> throw new IllegalArgumentException("不支持的 probe 模式：" + parts[2]);
            });
        }
        ModelProfileProbeResponse response = modelProfileProbeService.probe(parts[1], request);
        System.out.println(printer.formatModelProbe(response));
    }

    private void printNewEvents() {
        List<GameEventEntryResponse> events = adminGameInspectionService.getEvents(
                session.gameId(), adminInspectionCapability);
        events.stream()
                .filter(event -> event.getSeqNo() != null && event.getSeqNo() > session.lastPrintedEventSeqNo())
                .forEach(event -> {
                    long previous = session.lastPrintedEventSeqNo();
                    if (previous > 0 && event.getSeqNo() > previous + 1) {
                        System.out.printf(">>> [%d-%d] 中间事件属于私有信息，已按可见性策略隐藏%n",
                                previous + 1, event.getSeqNo() - 1);
                    }
                    synchronized (ConsoleModelStreamReporter.outputLock()) {
                        System.out.println(printer.formatEvent(event, session, false));
                    }
                    session.updateLastPrintedEventSeqNo(event.getSeqNo());
                });
    }

    private void printNewAudits() {
        List<GameAuditEntryResponse> auditEntries = adminGameInspectionService.getAudit(
                session.gameId(), adminInspectionCapability);
        auditEntries.stream()
                .filter(entry -> entry.getEventSeqNo() != null && entry.getEventSeqNo() > session.lastPrintedAuditEventSeqNo())
                .forEach(entry -> {
                    synchronized (ConsoleModelStreamReporter.outputLock()) {
                        System.out.println(printer.formatInlineThought(entry, session, session.logLevel()));
                    }
                    session.updateLastPrintedAuditEventSeqNo(entry.getEventSeqNo());
                });
    }

    private void printDecisionReportCommand() {
        String gameId = ensureActiveGame();
        GameStateResponse state = gameApplicationService.getState(gameId);
        printDecisionReport(state);
    }

    private void printDecisionReportIfTerminal(GameStateResponse after, String previousStatus) {
        if (after == null) {
            return;
        }
        boolean terminal = "ENDED".equals(after.getStatus()) || "PAUSED".equals(after.getStatus());
        if (!terminal) {
            return;
        }
        if (Objects.equals(previousStatus, after.getStatus())) {
            return;
        }
        printDecisionReport(after);
    }

    private void printDecisionReport(GameStateResponse state) {
        List<GameEventEntryResponse> events = gameApplicationService.getEvents(session.gameId());
        List<GameAuditEntryResponse> audits = adminGameInspectionService.getAudit(
                session.gameId(), adminInspectionCapability);
        List<ConsoleDecisionPlayer> players = loadDecisionReportPlayers();
        ConsoleDecisionReport report = decisionReportBuilder.build(state, events, audits, players);
        Path reportPath = resolveReportPath(session.gameId());
        String markdown = printer.formatDecisionReportMarkdown(report, session);
        writeDecisionReport(reportPath, markdown);
        System.out.println(printer.formatDecisionReport(report, session, reportPath));
    }

    private List<ConsoleDecisionPlayer> loadDecisionReportPlayers() {
        List<ConsoleDecisionPlayer> players = new ArrayList<>();
        for (int seatNo = 1; seatNo <= activePlayerCount(); seatNo++) {
            String playerId = "P" + seatNo;
            PlayerPrivateViewResponse view = adminGameInspectionService.getPlayerView(
                    session.gameId(), playerId, adminInspectionCapability);
            players.add(new ConsoleDecisionPlayer(
                    playerId,
                    view.getSeatNo() == null ? seatNo : view.getSeatNo(),
                    displayNameForReport(playerId),
                    view.getRoleSummary(),
                    stringValue(view.getPrivateKnowledge().get("camp")),
                    ConsoleKnowledgeFormatter.summarize(view.getPrivateKnowledge())
            ));
        }
        return players;
    }

    private int activePlayerCount() {
        if (!session.seats().isEmpty()) {
            return session.seats().size();
        }
        GameStateResponse state = gameApplicationService.getState(ensureActiveGame());
        Object rawPlayerCount = state.getPublicState() == null ? null : state.getPublicState().get("playerCount");
        return parsePositiveInt(rawPlayerCount, DEFAULT_PLAYER_COUNT);
    }

    private int parsePositiveInt(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue() > 0 ? number.intValue() : defaultValue;
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                int parsed = Integer.parseInt(string.trim());
                return parsed > 0 ? parsed : defaultValue;
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private List<String> distinctRoleIds(SetupTemplate setupTemplate) {
        return setupTemplate.roleIds().stream().distinct().toList();
    }

    private List<Integer> seatNumbers(int playerCount) {
        List<Integer> seatNos = new ArrayList<>();
        for (int seatNo = 1; seatNo <= playerCount; seatNo++) {
            seatNos.add(seatNo);
        }
        return seatNos;
    }

    private ClassicSetupSelection classicSetupSelection(int playerCount) {
        return new ClassicSetupSelection(
                "avalon-classic-%sp".formatted(playerCount),
                "classic-%sp".formatted(playerCount)
        );
    }

    private String displayNameForReport(String playerId) {
        String label = session.labelForPlayer(playerId);
        String prefix = playerId + "/";
        if (label != null && label.startsWith(prefix)) {
            return label.substring(prefix.length());
        }
        return playerId;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }

    private Path resolveReportPath(String gameId) {
        return reportOutputDir.resolve(gameId + "-decision-report.md").toAbsolutePath().normalize();
    }

    private void writeDecisionReport(Path reportPath, String markdown) {
        try {
            Files.createDirectories(reportPath.getParent());
            Files.writeString(reportPath, markdown, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("写入 Markdown 报告失败: " + reportPath, exception);
        }
    }

    private String ensureActiveGame() {
        if (!session.hasActiveGame()) {
            throw new IllegalStateException("当前没有活动游戏。请先执行 `new` 或 `use <gameId>`。");
        }
        return session.gameId();
    }

    private String promptString(BufferedReader reader, String prompt, String defaultValue) throws IOException {
        System.out.print(prompt);
        System.out.flush();
        String raw = reader.readLine();
        if (raw == null) {
            throw new IllegalStateException("输入流已关闭");
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }

    private long promptLong(BufferedReader reader, String prompt, long defaultValue) throws IOException {
        while (true) {
            String raw = promptString(reader, prompt, String.valueOf(defaultValue));
            try {
                return Long.parseLong(raw);
            } catch (NumberFormatException ignored) {
                System.out.println("请输入合法整数。");
            }
        }
    }

    private long promptOptionalLong(BufferedReader reader, String prompt) throws IOException {
        while (true) {
            System.out.print(prompt);
            System.out.flush();
            String raw = reader.readLine();
            if (raw == null) {
                throw new IllegalStateException("输入流已关闭");
            }
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                return seedGenerator.nextSeed();
            }
            try {
                return Long.parseLong(trimmed);
            } catch (NumberFormatException ignored) {
                System.out.println("请输入合法整数，或直接回车使用自动生成值。");
            }
        }
    }

    private enum SeatPreset {
        CUSTOM,
        SCRIPTED,
        NOOP_LLM,
        SEAT_BOUND_MODEL_POOL,
        ROLE_BOUND_MODEL_POOL,
        RANDOM_MODEL_POOL
    }

    private enum SeatMode {
        SCRIPTED,
        NOOP_LLM,
        MODEL_POOL_LLM
    }

    private record SeatInput(int seatNo, String displayName, String controllerType, PlayerAgentConfig agentConfig) {
    }

    private record CreateRequestDraft(List<SeatInput> seatInputs, CreateGameRequest.LlmSelectionRequest llmSelection) {
    }

    private record ClassicSetupSelection(String ruleSetId, String setupTemplateId) {
    }
}
