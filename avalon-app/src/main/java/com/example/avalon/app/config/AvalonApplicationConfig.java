package com.example.avalon.app.config;

import com.example.avalon.agent.controller.LlmPlayerController;
import com.example.avalon.agent.gateway.AgentGateway;
import com.example.avalon.agent.gateway.ModelProfileApiKeyResolver;
import com.example.avalon.agent.model.PlayerAgentConfig;
import com.example.avalon.agent.service.AgentTurnRequestFactory;
import com.example.avalon.agent.service.PromptBuilder;
import com.example.avalon.agent.service.ResponseParser;
import com.example.avalon.agent.service.ValidationRetryPolicy;
import com.example.avalon.agent.harness.AgentHarness;
import com.example.avalon.agent.harness.DefaultAgentHarness;
import com.example.avalon.api.service.LlmSelectionResolutionService;
import com.example.avalon.api.service.SeedGenerator;
import com.example.avalon.config.io.YamlConfigLoader;
import com.example.avalon.config.model.AvalonConfigRegistry;
import com.example.avalon.config.service.SetupValidationService;
import com.example.avalon.core.player.enums.PlayerControllerType;
import com.example.avalon.persistence.store.AuditRecordStore;
import com.example.avalon.persistence.store.GameEventStore;
import com.example.avalon.persistence.store.GameSnapshotStore;
import com.example.avalon.persistence.store.PlayerMemorySnapshotStore;
import com.example.avalon.runtime.controller.PlayerControllerResolver;
import com.example.avalon.runtime.coordination.ActionCollector;
import com.example.avalon.runtime.coordination.DefaultGameCoordinator;
import com.example.avalon.runtime.coordination.GameCoordinator;
import com.example.avalon.runtime.coordination.SqliteActionCollector;
import com.example.avalon.runtime.engine.ConfigDrivenGameRuleEngine;
import com.example.avalon.runtime.engine.GameRuleEngine;
import com.example.avalon.runtime.engine.RoleAssignmentService;
import com.example.avalon.runtime.engine.VisibilityService;
import com.example.avalon.runtime.orchestrator.GameOrchestrator;
import com.example.avalon.runtime.persistence.RuntimePersistenceService;
import com.example.avalon.runtime.persistence.RuntimeStateCodec;
import com.example.avalon.runtime.recovery.RecoveryService;
import com.example.avalon.runtime.recovery.ReplayQueryService;
import com.example.avalon.runtime.disclosure.DefaultDisclosurePolicy;
import com.example.avalon.runtime.service.GameSessionService;
import com.example.avalon.runtime.service.ResolvedLlmConfigInitializer;
import com.example.avalon.runtime.service.TurnContextBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class AvalonApplicationConfig {
    @Bean
    AvalonConfigRegistry avalonConfigRegistry() {
        YamlConfigLoader loader = new YamlConfigLoader(new SetupValidationService());
        return loader.loadAndValidate(resolveResourcesPath());
    }

    @Bean
    ModelProfileApiKeyResolver modelProfileApiKeyResolver(Environment environment,
                                                          @Value("${avalon.model-profile-secrets.path:}") String configuredSecretsPath) {
        return new FileBackedModelProfileApiKeyResolver(
                resolveSecretsPath(configuredSecretsPath),
                environment::getProperty,
                System::getenv
        );
    }

    @Bean
    GameSessionService gameSessionService() {
        return new GameSessionService();
    }

    @Bean
    GameRuleEngine gameRuleEngine() {
        return new ConfigDrivenGameRuleEngine();
    }

    @Bean
    RoleAssignmentService roleAssignmentService() {
        return new RoleAssignmentService();
    }

    @Bean
    VisibilityService visibilityService() {
        return new VisibilityService();
    }

    @Bean
    PlayerControllerResolver playerControllerResolver(
            AgentGateway agentGateway,
            AgentTurnRequestFactory agentTurnRequestFactory,
            PromptBuilder promptBuilder,
            ResponseParser responseParser,
            ValidationRetryPolicy validationRetryPolicy,
            AgentHarness agentHarness
    ) {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        PlayerControllerResolver resolver = new PlayerControllerResolver();
        resolver.registerFactory(PlayerControllerType.LLM, (state, player) -> new LlmPlayerController(
                agentHarness,
                objectMapper.convertValue(
                        state.resolvedLlmControllerConfigOf(player.playerId()) == null
                                ? player.controllerConfig()
                                : state.resolvedLlmControllerConfigOf(player.playerId()),
                        PlayerAgentConfig.class)
        ));
        return resolver;
    }

    @Bean
    AgentHarness agentHarness(AgentGateway agentGateway, AgentTurnRequestFactory requestFactory,
                              PromptBuilder promptBuilder, ResponseParser responseParser,
                              ValidationRetryPolicy validationRetryPolicy) {
        return new DefaultAgentHarness(requestFactory, promptBuilder, agentGateway, responseParser, validationRetryPolicy);
    }

    @Bean
    TurnContextBuilder turnContextBuilder(VisibilityService visibilityService) {
        return new TurnContextBuilder(visibilityService);
    }

    @Bean
    RuntimeStateCodec runtimeStateCodec() {
        return new RuntimeStateCodec();
    }

    @Bean
    RuntimePersistenceService runtimePersistenceService(
            GameEventStore gameEventStore,
            GameSnapshotStore gameSnapshotStore,
            PlayerMemorySnapshotStore playerMemorySnapshotStore,
            AuditRecordStore auditRecordStore,
            RuntimeStateCodec runtimeStateCodec
    ) {
        return new RuntimePersistenceService(gameEventStore, gameSnapshotStore, playerMemorySnapshotStore, auditRecordStore, runtimeStateCodec);
    }

    @Bean
    RecoveryService recoveryService(
            GameSnapshotStore gameSnapshotStore,
            GameEventStore gameEventStore,
            PlayerMemorySnapshotStore playerMemorySnapshotStore,
            RuntimeStateCodec runtimeStateCodec
    ) {
        return new RecoveryService(gameSnapshotStore, gameEventStore, playerMemorySnapshotStore, runtimeStateCodec);
    }

    @Bean
    ReplayQueryService replayQueryService(GameEventStore gameEventStore, AuditRecordStore auditRecordStore) {
        return new ReplayQueryService(gameEventStore, auditRecordStore, new DefaultDisclosurePolicy());
    }

    @Bean
    SeedGenerator seedGenerator() {
        return () -> ThreadLocalRandom.current().nextLong(1L, Long.MAX_VALUE);
    }

    @Bean
    GameOrchestrator gameOrchestrator(
            GameSessionService gameSessionService,
            GameRuleEngine gameRuleEngine,
            RoleAssignmentService roleAssignmentService,
            VisibilityService visibilityService,
            PlayerControllerResolver playerControllerResolver,
            LlmSelectionResolutionService llmSelectionResolutionService
    ) {
        return new GameOrchestrator(
                gameSessionService,
                gameRuleEngine,
                roleAssignmentService,
                visibilityService,
                playerControllerResolver,
                llmSelectionResolutionService
        );
    }

    @Bean
    ActionCollector actionCollector(org.springframework.jdbc.core.JdbcTemplate jdbcTemplate) {
        return new SqliteActionCollector(jdbcTemplate);
    }

    @Bean
    GameCoordinator gameCoordinator(GameSessionService gameSessionService,
                                    GameOrchestrator gameOrchestrator,
                                    PlayerControllerResolver playerControllerResolver,
                                    TurnContextBuilder turnContextBuilder,
                                    ActionCollector actionCollector,
                                    ExecutorService avalonAgentExecutor) {
        return new DefaultGameCoordinator(gameSessionService, gameOrchestrator, playerControllerResolver,
                turnContextBuilder, actionCollector, avalonAgentExecutor);
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService avalonAgentExecutor(@Value("${avalon.agent.parallelism:5}") int parallelism) {
        return Executors.newFixedThreadPool(Math.max(1, parallelism));
    }

    @Bean
    org.springframework.beans.factory.InitializingBean sqlitePragmas(JdbcTemplate jdbcTemplate) {
        return () -> {
            jdbcTemplate.execute("PRAGMA journal_mode=WAL");
            jdbcTemplate.execute("PRAGMA busy_timeout=5000");
        };
    }

    private Path resolveResourcesPath() {
        Path[] candidates = new Path[] {
                Path.of("avalon-app", "src", "main", "resources"),
                Path.of("src", "main", "resources")
        };
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to locate avalon-app resources directory");
    }

    private Path resolveSecretsPath(String configuredSecretsPath) {
        Path projectRoot = resolveProjectRoot();
        if (configuredSecretsPath == null || configuredSecretsPath.isBlank()) {
            return projectRoot.resolve("avalon-model-profile-secrets.yml").normalize();
        }
        Path configuredPath = Path.of(configuredSecretsPath.trim());
        return configuredPath.isAbsolute()
                ? configuredPath.normalize()
                : projectRoot.resolve(configuredPath).normalize();
    }

    private Path resolveProjectRoot() {
        Path current = resolveResourcesPath().toAbsolutePath().normalize();
        for (int level = 0; level < 4; level++) {
            current = current.getParent();
            if (current == null) {
                throw new IllegalStateException("Unable to resolve project root from avalon-app resources directory");
            }
        }
        return current;
    }
}
