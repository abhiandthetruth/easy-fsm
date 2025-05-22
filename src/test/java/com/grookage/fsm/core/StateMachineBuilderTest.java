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
import com.grookage.fsm.core.stubs.TestContext;
import com.grookage.fsm.core.stubs.TestEvent;
import com.grookage.fsm.core.stubs.TestHub;
import com.grookage.fsm.core.stubs.TestState;
import com.grookage.fsm.core.stubs.TestTransitionKey;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class StateMachineBuilderTest {

    private MachineBuilderConfig<TestState, TestEvent> basicMachineConfig;

    @Before
    public void setUp() {
        basicMachineConfig = new MachineBuilderConfig<>();
        basicMachineConfig.setName("TestFSM");
        basicMachineConfig.setStartState(TestState.STARTED);
        basicMachineConfig.setEndStates(Sets.newHashSet(TestState.COMPLETED, TestState.FAILED));
        basicMachineConfig.setTransitionConfigs(List.of(
                new TransitionConfig<>(TestEvent.INITIATE, TestState.STARTED, TestState.CREATED),
                new TransitionConfig<>(TestEvent.MOVE_TO_PROGRESS, TestState.CREATED, TestState.IN_PROGRESS),
                new TransitionConfig<>(TestEvent.MOVE_TO_COMPLETED, TestState.IN_PROGRESS, TestState.COMPLETED),
                new TransitionConfig<>(TestEvent.MOVE_TO_FAILED, TestState.IN_PROGRESS, TestState.FAILED)
        ));
    }

    @Test
    public void testValidStateMachineBuilder() throws Exception {
        final var machineBuilderConfig = ResourceHelper.getResource("stateMachine.json", new TypeReference<MachineBuilderConfig<TestState, TestEvent>>() {
        });
        Assert.assertNotNull(machineBuilderConfig);
        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(machineBuilderConfig)
                .withTransitionProcessorHub(TestHub.builder().build())
                .build();
        Assert.assertNotNull(stateMachine);
    }

    @Test(expected = InvalidStateException.class)
    public void testForInvalidStateMachine() throws Exception {
        final var machineBuilderConfig = ResourceHelper.getResource("invalidMachine.json", new TypeReference<MachineBuilderConfig<TestState, TestEvent>>() {
        });
        new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(machineBuilderConfig)
                .withTransitionProcessorHub(TestHub.builder().build())
                .build();
    }

    @Test
    public void testWithBeforeAnyTransitionAction() {
        final var actionExecuted = new AtomicBoolean(false);
        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(TestHub.builder().build())
                .withBeforeAnyTransitionAction((from, to, event, context) -> actionExecuted.set(true))
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(context);
        Assert.assertTrue(actionExecuted.get());
    }

    @Test
    public void testWithAfterAnyTransitionAction() {
        final var actionExecuted = new AtomicBoolean(false);
        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(TestHub.builder().build())
                .withAfterAnyTransitionAction((from, to, event, context) -> actionExecuted.set(true))
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(context);
        Assert.assertTrue(actionExecuted.get());
    }

    @Test
    public void testWithBeforeStateTransitionAction() {
        final var actionExecuted = new AtomicBoolean(false);
        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(TestHub.builder().build())
                .withBeforeStateTransitionAction(TestState.CREATED, (from, to, event, context) -> {
                    Assert.assertEquals(TestState.CREATED, to);
                    actionExecuted.set(true);
                })
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(context);
        Assert.assertTrue(actionExecuted.get());
    }

    @Test
    public void testWithAfterStateTransitionAction() {
        final var actionExecuted = new AtomicBoolean(false);
        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(TestHub.builder().build())
                .withAfterStateTransitionAction(TestState.STARTED, (from, to, event, context) -> {
                    Assert.assertEquals(TestState.STARTED, from);
                    actionExecuted.set(true);
                })
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(context);
        Assert.assertTrue(actionExecuted.get());
    }

    @Test
    public void testWithStateTransitionAction() {
        final var actionExecuted = new AtomicBoolean(false);
        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(TestHub.builder().build())
                .withStateTransitionAction(TestState.STARTED, TestEvent.INITIATE, context -> {
                    Assert.assertEquals(TestState.STARTED, context.getFrom());
                    Assert.assertEquals(TestEvent.INITIATE, context.getCausedEvent());
                    actionExecuted.set(true);
                })
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(context);
        Assert.assertTrue(actionExecuted.get());
    }

    @Test
    public void testWithAnyStateTransitionAction_AdditionalAction() {
        final var defaultActionExecuted = new AtomicBoolean(false); // The one from builder's eventAction
        final var additionalActionExecuted = new AtomicBoolean(false);

        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(TestHub.builder().build())
                .withEventAction(context -> defaultActionExecuted.set(true)) // Sets the default EventAction
                .withAnyStateTransitionAction(context -> additionalActionExecuted.set(true)) // Adds another one
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(context);

        Assert.assertTrue("Default EventAction should be executed", defaultActionExecuted.get());
        Assert.assertTrue("Additional AnyStateTransitionAction should be executed", additionalActionExecuted.get());
    }

    @Test
    public void testWithFinalStateAction() {
        final var actionExecuted = new AtomicBoolean(false);
        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(TestHub.builder().build())
                .withFinalStateAction(TestState.COMPLETED, (finalState, context) -> {
                    Assert.assertEquals(TestState.COMPLETED, finalState);
                    actionExecuted.set(true);
                })
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.IN_PROGRESS); // State before final state
        context.setCausedEvent(TestEvent.MOVE_TO_COMPLETED); // Event to reach final state
        // Manually set current state in engine for accurate final state check
        stateMachine.getStateEngine().getStateManagementService().setFrom(TestState.IN_PROGRESS);
        stateMachine.fire(context);
        Assert.assertTrue(actionExecuted.get());
    }

    @Test
    public void testWithErrorAction_CustomDefault() {
        final var customErrorActionExecuted = new AtomicBoolean(false);
        final AtomicReference<FsmException> capturedException = new AtomicReference<>();

        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
            .withMachineBuilderConfig(basicMachineConfig)
            .withTransitionProcessorHub(TestHub.builder().build())
            .withErrorAction((exception, context) -> { // This sets the *default* ErrorAction
                customErrorActionExecuted.set(true);
                capturedException.set(exception);
            })
            // Add a specific action that will throw an error
            .withAfterStateTransitionAction(TestState.STARTED, (from, to, event, ctx) -> {
                throw new RuntimeException("Simulated error");
            })
            .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE); // This transition will trigger the error

        stateMachine.fire(context);

        Assert.assertTrue("Custom default ErrorAction should be executed", customErrorActionExecuted.get());
        Assert.assertNotNull(capturedException.get());
        Assert.assertTrue(capturedException.get().getMessage().contains("Simulated error"));
    }

    @Test
    public void testMultipleActionsViaBuilder_AllExecuted() {
        final var beforeAny1 = new AtomicBoolean(false);
        final var beforeAny2 = new AtomicBoolean(false);
        final var afterAny1 = new AtomicBoolean(false);
        final var afterAny2 = new AtomicBoolean(false);

        final var stateMachine = new StateMachineBuilder<TestState, TestEvent, TestTransitionKey, TestContext>()
                .withMachineBuilderConfig(basicMachineConfig)
                .withTransitionProcessorHub(TestHub.builder().build())
                .withBeforeAnyTransitionAction((from, to, event, context) -> beforeAny1.set(true))
                .withBeforeAnyTransitionAction((from, to, event, context) -> beforeAny2.set(true))
                .withAfterAnyTransitionAction((from, to, event, context) -> afterAny1.set(true))
                .withAfterAnyTransitionAction((from, to, event, context) -> afterAny2.set(true))
                .build();

        TestContext context = new TestContext();
        context.setFrom(TestState.STARTED);
        context.setCausedEvent(TestEvent.INITIATE);
        stateMachine.fire(context);

        Assert.assertTrue(beforeAny1.get());
        Assert.assertTrue(beforeAny2.get());
        Assert.assertTrue(afterAny1.get());
        Assert.assertTrue(afterAny2.get());
    }
}
