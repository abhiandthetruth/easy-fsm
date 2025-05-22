/*
 * Copyright 2022 Koushik R <rkoushik.14@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.grookage.fsm.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.grookage.fsm.core.config.MachineBuilderConfig;
import com.grookage.fsm.core.exceptions.InvalidStateException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.Sets;
import com.grookage.fsm.core.config.MachineBuilderConfig;
import com.grookage.fsm.core.config.TransitionConfig;
import com.grookage.fsm.core.exceptions.FsmException;
import com.grookage.fsm.core.exceptions.InvalidStateException;
import com.grookage.fsm.core.helpers.ResourceHelper;
import com.grookage.fsm.core.models.executors.ErrorAction;
import com.grookage.fsm.core.stubs.TestContext;
import com.grookage.fsm.core.stubs.TestEvent;
import com.grookage.fsm.core.stubs.TestHub;
import com.grookage.fsm.core.stubs.TestState;
import com.grookage.fsm.core.stubs.TestTransitionKey;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class StateMachineBuilderTest {

    private MachineBuilderConfig<TestState, TestEvent> basicMachineConfig;
    private TransitionProcessorHub<TestState, TestEvent, TestTransitionKey, TestContext> testHub;

    @Before
    public void setUp() {
        basicMachineConfig = new MachineBuilderConfig<>();
        basicMachineConfig.setName("TestFSM_Builder");
        basicMachineConfig.setStartState(TestState.STARTED);
        basicMachineConfig.setEndStates(Sets.newHashSet(TestState.COMPLETED, TestState.FAILED));
        basicMachineConfig.setTransitionConfigs(List.of(
                new TransitionConfig<>(TestEvent.INITIATE, TestState.STARTED, TestState.CREATED),
                new TransitionConfig<>(TestEvent.MOVE_TO_PROGRESS, TestState.CREATED, TestState.IN_PROGRESS),
                new TransitionConfig<>(TestEvent.MOVE_TO_COMPLETED, TestState.IN_PROGRESS, TestState.COMPLETED),
                new TransitionConfig<>(TestEvent.MOVE_TO_FAILED, TestState.IN_PROGRESS, TestState.FAILED)
        ));
        testHub = TestHub.builder().build();
    }

    @Test
    public void testValidStateMachineBuilder() throws Exception {
        final var machineBuilderConfig = ResourceHelper.getResource("stateMachine.json", new TypeReference<MachineBuilderConfig<TestState, TestEvent>>() {
        });
        Assert.assertNotNull(machineBuilderConfig);
        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(machineBuilderConfig)
                .withTransitionProcessorHub(testHub)
                .build();
        Assert.assertNotNull(stateMachine);
    }

    @Test(expected = InvalidStateException.class)
    public void testForInvalidStateMachine() throws Exception {
        final var machineBuilderConfig = ResourceHelper.getResource("invalidMachine.json", new TypeReference<MachineBuilderConfig<TestState, TestEvent>>() {
        });
        new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(machineBuilderConfig)
                .withTransitionProcessorHub(testHub)
                .build();
    }

    @Test
    public void testBuilder_withBeforeAnyTransitionAction() {
        final var actionExecuted = new AtomicBoolean(false);
        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(testHub)
                .withBeforeAnyTransitionAction(context -> actionExecuted.set(true))
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(context);
        Assert.assertTrue("withBeforeAnyTransitionAction should be executed", actionExecuted.get());
    }

    @Test
    public void testBuilder_withAfterAnyTransitionAction() {
        final var actionExecuted = new AtomicBoolean(false);
        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(testHub)
                .withAfterAnyTransitionAction(context -> actionExecuted.set(true))
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(context);
        Assert.assertTrue("withAfterAnyTransitionAction should be executed", actionExecuted.get());
    }

    @Test
    public void testBuilder_withBeforeStateTransitionAction() {
        final var correctActionExecuted = new AtomicBoolean(false);
        final var wrongActionExecuted = new AtomicBoolean(false);
        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(testHub)
                .withBeforeStateTransitionAction(TestState.CREATED, context -> correctActionExecuted.set(true))
                .withBeforeStateTransitionAction(TestState.IN_PROGRESS, context -> wrongActionExecuted.set(true))
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE); // Transitions to CREATED
        stateMachine.fire(context);
        Assert.assertTrue("Correct withBeforeStateTransitionAction should be executed", correctActionExecuted.get());
        Assert.assertFalse("Wrong withBeforeStateTransitionAction should NOT be executed", wrongActionExecuted.get());
    }

    @Test
    public void testBuilder_withAfterStateTransitionAction() {
        final var correctActionExecuted = new AtomicBoolean(false);
        final var wrongActionExecuted = new AtomicBoolean(false);
        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(testHub)
                .withAfterStateTransitionAction(TestState.STARTED, context -> correctActionExecuted.set(true))
                .withAfterStateTransitionAction(TestState.CREATED, context -> wrongActionExecuted.set(true))
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(context);
        Assert.assertTrue("Correct withAfterStateTransitionAction should be executed", correctActionExecuted.get());
        Assert.assertFalse("Wrong withAfterStateTransitionAction should NOT be executed", wrongActionExecuted.get());
    }

    @Test
    public void testBuilder_withAnyStateTransitionAction_OverridesDefaultEventAction() {
        // The eventAction passed to StateMachine constructor via builder.withEventAction() is registered by StateMachine.start()
        // using stateEngine.anyTransition().
        // If builder.withAnyStateTransitionAction() is also used, the one from withAnyStateTransitionAction
        // will be registered *after* the default one from withEventAction (if any) during the build process.
        // Since ActionService overwrites, the one from withAnyStateTransitionAction should be the one that executes.
        final var defaultEventActionExecuted = new AtomicBoolean(false);
        final var specificAnyStateActionExecuted = new AtomicBoolean(false);

        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(testHub)
                .withEventAction(context -> defaultEventActionExecuted.set(true)) // This is the "default" one
                .withAnyStateTransitionAction(context -> specificAnyStateActionExecuted.set(true)) // This should take precedence
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(context);

        Assert.assertTrue("Specific anyStateTransitionAction from builder should be executed", specificAnyStateActionExecuted.get());
        Assert.assertFalse("Default eventAction from builder should NOT be executed if overridden by withAnyStateTransitionAction", defaultEventActionExecuted.get());
    }
    
    @Test
    public void testBuilder_withOnlyDefaultEventAction() {
        // Verifies that the default eventAction provided via withEventAction is used if no specific withAnyStateTransitionAction is set.
        final var defaultEventActionExecuted = new AtomicBoolean(false);

        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(testHub)
                .withEventAction(context -> defaultEventActionExecuted.set(true)) // This is the "default" one
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(context);

        Assert.assertTrue("Default eventAction from builder should be executed", defaultEventActionExecuted.get());
    }


    @Test
    public void testBuilder_withOnStateTransitionAction_FromState() {
        final var actionExecuted = new AtomicBoolean(false);
        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(testHub)
                .withOnStateTransitionAction(TestState.STARTED, context -> actionExecuted.set(true))
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(context);
        Assert.assertTrue("withOnStateTransitionAction(fromState) should be executed", actionExecuted.get());
    }

    @Test
    public void testBuilder_withOnStateTransitionAction_Event_FromState() {
        final var correctActionExecuted = new AtomicBoolean(false);
        final var wrongActionExecuted = new AtomicBoolean(false);
        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(testHub)
                .withOnStateTransitionAction(TestEvent.INITIATE, TestState.STARTED, context -> correctActionExecuted.set(true))
                .withOnStateTransitionAction(TestEvent.MOVE_TO_PROGRESS, TestState.STARTED, context -> wrongActionExecuted.set(true))
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(context);
        Assert.assertTrue("Correct withOnStateTransitionAction(event, fromState) should be executed", correctActionExecuted.get());
        Assert.assertFalse("Wrong withOnStateTransitionAction(event, fromState) should NOT be executed", wrongActionExecuted.get());
    }

    @Test
    public void testBuilder_withOnFinalStateReachedAction() {
        final var actionExecuted = new AtomicBoolean(false);
        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(testHub)
                .withOnFinalStateReachedAction(TestState.COMPLETED, context -> actionExecuted.set(true))
                .build();

        // Sequence to reach final state
        TestContext ctx1 = new TestContext();
        ctx1.setFrom(TestState.STARTED);
        ctx1.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(ctx1);

        TestContext ctx2 = new TestContext();
        ctx2.setFrom(TestState.CREATED);
        ctx2.setCausedEvent(TestEvent.MOVE_TO_PROGRESS);
        stateMachine.fire(ctx2);

        TestContext finalContext = new TestContext();
        finalContext.setFrom(TestState.IN_PROGRESS);
        finalContext.setCausedEvent(TestEvent.MOVE_TO_COMPLETED);
        stateMachine.fire(finalContext);

        Assert.assertTrue("withOnFinalStateReachedAction should be executed", actionExecuted.get());
    }

    @Test
    public void testBuilder_withErrorAction_OverridesDefaultErrorAction() {
        // Similar to withAnyStateTransitionAction, withErrorAction sets the ErrorAction
        // that will be passed to StateMachine constructor. StateMachine.start() registers this.
        // If builder had a separate "addAdditionalErrorAction", that would be clearer.
        // For now, withErrorAction sets the one that StateMachine.start() will use.
        final var specificErrorActionExecuted = new AtomicBoolean(false);

        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(testHub)
                .withErrorAction((ErrorAction<TestEvent, TestState, TestTransitionKey, TestContext>) (error, context) -> specificErrorActionExecuted.set(true))
                .withAfterStateTransitionAction(TestState.STARTED, context -> { // Action to cause an error
                    throw new RuntimeException("Simulated error during transition");
                })
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE);
        try {
            stateMachine.fire(context);
        } catch (Exception e) {
            // Expected
        }
        Assert.assertTrue("Specific errorAction from builder should be executed", specificErrorActionExecuted.get());
    }
}
