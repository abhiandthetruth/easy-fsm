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

import com.google.common.base.Preconditions;
import com.grookage.fsm.core.config.MachineBuilderConfig;
import com.grookage.fsm.core.hubs.TransitionProcessorHub;
import com.grookage.fsm.core.models.entities.Context;
import com.google.common.base.Preconditions;
import com.grookage.fsm.core.config.MachineBuilderConfig;
import com.grookage.fsm.core.hubs.TransitionProcessorHub;
import com.grookage.fsm.core.models.entities.Context;
import com.grookage.fsm.core.models.entities.Event;
import com.grookage.fsm.core.models.entities.State;
import com.grookage.fsm.core.models.entities.TransitionKey;
import com.grookage.fsm.core.models.executors.AfterStateTransitionAction;
import com.grookage.fsm.core.models.executors.BeforeStateTransitionAction;
import com.grookage.fsm.core.models.executors.ErrorAction;
import com.grookage.fsm.core.models.executors.EventAction;
import com.grookage.fsm.core.models.executors.FinalStateAction;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
public class StateMachineBuilder<S extends State, E extends Event, K extends TransitionKey, C extends Context<S, E, K>> {

    // Helper classes for storing actions with their context
    @AllArgsConstructor
    @Getter
    private static class StateSpecificAction<STATE extends State, ACTION> {
        private STATE state;
        private ACTION action;
    }

    @AllArgsConstructor
    @Getter
    private static class TransitionSpecificAction<STATE extends State, EVENT extends Event, ACTION> {
        private STATE fromState;
        private EVENT event;
        private ACTION action;
    }

    private MachineBuilderConfig<S, E> machineBuilderConfig;
    private TransitionProcessorHub<S, E, K, C> transitionProcessorHub;
    private ErrorAction<C> errorAction; // Default error action
    private EventAction<E, S, K, C> eventAction; // Default event action (for ANY_STATE_TRANSITION)

    // Collections for additional actions
    private final List<BeforeStateTransitionAction<S, E, K, C>> beforeAnyTransitionActions = new ArrayList<>();
    private final List<AfterStateTransitionAction<S, E, K, C>> afterAnyTransitionActions = new ArrayList<>();
    private final List<StateSpecificAction<S, BeforeStateTransitionAction<S, E, K, C>>> beforeStateTransitionActions = new ArrayList<>();
    private final List<StateSpecificAction<S, AfterStateTransitionAction<S, E, K, C>>> afterStateTransitionActions = new ArrayList<>();
    private final List<TransitionSpecificAction<S, E, EventAction<E, S, K, C>>> stateTransitionActions = new ArrayList<>();
    private final List<EventAction<E, S, K, C>> anyStateTransitionActions = new ArrayList<>();
    private final List<StateSpecificAction<S, FinalStateAction<S, E, K, C>>> finalStateActions = new ArrayList<>();


    @Getter
    private StateMachine<S, E, K, C> stateMachine;

    public StateMachineBuilder<S, E, K, C> withMachineBuilderConfig(MachineBuilderConfig<S, E> machineBuilderConfig) {
        this.machineBuilderConfig = machineBuilderConfig;
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withTransitionProcessorHub(TransitionProcessorHub<S, E, K, C> transitionProcessorHub){
        this.transitionProcessorHub = transitionProcessorHub;
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withEventAction(EventAction<E, S, K, C> eventAction){
        this.eventAction = eventAction;
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withErrorAction(ErrorAction<E, S, K, C> errorAction) {
        this.errorAction = errorAction; // This sets the default error action
        return this;
    }

    // Methods to add specific actions

    public StateMachineBuilder<S, E, K, C> withBeforeAnyTransitionAction(BeforeStateTransitionAction<S, E, K, C> action) {
        this.beforeAnyTransitionActions.add(action);
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withAfterAnyTransitionAction(AfterStateTransitionAction<S, E, K, C> action) {
        this.afterAnyTransitionActions.add(action);
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withBeforeStateTransitionAction(S to, BeforeStateTransitionAction<S, E, K, C> action) {
        this.beforeStateTransitionActions.add(new StateSpecificAction<>(to, action));
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withAfterStateTransitionAction(S from, AfterStateTransitionAction<S, E, K, C> action) {
        this.afterStateTransitionActions.add(new StateSpecificAction<>(from, action));
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withStateTransitionAction(S from, E event, EventAction<E, S, K, C> action) {
        this.stateTransitionActions.add(new TransitionSpecificAction<>(from, event, action));
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withAnyStateTransitionAction(EventAction<E, S, K, C> action) {
        this.anyStateTransitionActions.add(action);
        return this;
    }

    public StateMachineBuilder<S, E, K, C> withFinalStateAction(S finalState, FinalStateAction<S, E, K, C> action) {
        this.finalStateActions.add(new StateSpecificAction<>(finalState, action));
        return this;
    }

    public StateMachine<S, E, K, C> build() {
        Preconditions.checkNotNull(machineBuilderConfig, "Machine Builder Config can't be null");
        final var startState = machineBuilderConfig.getStartState();
        final var endStates = machineBuilderConfig.getEndStates();

        // Create StateMachine with default error and event actions
        this.stateMachine = new StateMachine<>(machineBuilderConfig.getName(),
                startState, transitionProcessorHub, this.errorAction, this.eventAction);

        // Configure basic transitions
        final var transitionConfigs = machineBuilderConfig.getTransitionConfigs();
        transitionConfigs.forEach(transitionConfig ->
                stateMachine.onTransition(transitionConfig.getCausedEvent(), transitionConfig.getFrom(), transitionConfig.getTo()));

        // Set end states
        this.stateMachine.end(endStates);

        // Register all collected actions
        // Note: The default errorAction is already passed to StateMachine constructor and registered in its start()
        // The default eventAction is also passed and registered for ANY_STATE_TRANSITION in StateMachine's start()

        beforeAnyTransitionActions.forEach(stateMachine::onBeforeAnyTransition);
        afterAnyTransitionActions.forEach(stateMachine::onAfterAnyTransition);

        for (StateSpecificAction<S, BeforeStateTransitionAction<S, E, K, C>> item : beforeStateTransitionActions) {
            stateMachine.onBeforeStateTransition(item.getState(), item.getAction());
        }

        for (StateSpecificAction<S, AfterStateTransitionAction<S, E, K, C>> item : afterStateTransitionActions) {
            stateMachine.onAfterStateTransition(item.getState(), item.getAction());
        }

        for (TransitionSpecificAction<S, E, EventAction<E, S, K, C>> item : stateTransitionActions) {
            stateMachine.onStateTransition(item.getFromState(), item.getEvent(), item.getAction());
        }

        // These will be added in addition to the default eventAction registered in StateMachine.start()
        anyStateTransitionActions.forEach(stateMachine::onAnyStateTransition);

        for (StateSpecificAction<S, FinalStateAction<S, E, K, C>> item : finalStateActions) {
            stateMachine.onFinalState(item.getState(), item.getAction());
        }

        // Start the state machine (which also registers default actions if not overridden by specific withErrorAction)
        this.stateMachine.start();
        return stateMachine;
    }
}
