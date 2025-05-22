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
import com.google.common.base.Preconditions;
import com.grookage.fsm.core.action.DefaultErrorAction;
import com.grookage.fsm.core.engine.StateEngine;
import com.grookage.fsm.core.hubs.TransitionProcessorHub;
import com.grookage.fsm.core.models.entities.Context;
import com.grookage.fsm.core.models.entities.Event;
import com.grookage.fsm.core.models.entities.State;
import com.grookage.fsm.core.models.entities.Transition;
import com.grookage.fsm.core.models.entities.TransitionKey;
import com.grookage.fsm.core.models.executors.AfterStateTransitionAction;
import com.grookage.fsm.core.models.executors.BeforeStateTransitionAction;
import com.grookage.fsm.core.models.executors.ErrorAction;
import com.grookage.fsm.core.models.executors.EventAction;
import com.grookage.fsm.core.models.executors.FinalStateAction;
import com.grookage.fsm.core.services.ActionService;
import com.grookage.fsm.core.services.StateManagementService;
import com.grookage.fsm.core.services.TransitionService;
import lombok.Data;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Locale;

@Data
@Slf4j
@Getter
public class StateMachine<S extends State, E extends Event, K extends TransitionKey, C extends Context<S, E, K>> {

  private final String name;
  private final StateEngine<E, S, K, C> stateEngine;
  private final TransitionProcessorHub<S, E, K, C> transitionProcessorHub;
  private ErrorAction<C> errorAction; // Changed generic type
  private EventAction<E, S, K, C> eventAction;

  public StateMachine(
      final String name,
      final S startState,
      final TransitionProcessorHub<S, E, K, C> transitionProcessorHub,
      ErrorAction<C> errorAction, // Changed generic type
      EventAction<E, S, K, C> eventAction) {
    this.name = name.toUpperCase(Locale.ROOT);
    this.stateEngine = new StateEngine<>(
        startState,
        new TransitionService<>(),
        new StateManagementService<>(),
        new ActionService<>()
    );
    this.transitionProcessorHub = transitionProcessorHub;
    this.errorAction = null != errorAction ? errorAction : new DefaultErrorAction<C>(); // Changed generic type
    this.eventAction = null != eventAction ? eventAction : context -> {
      log.info("Default EventAction: Processing fsm transition for event {} moving from {} to {}",
          context.getCausedEvent(), context.getFrom(), context.getTo());
      final var processor = transitionProcessorHub.getProcessor(context);
      if (null == processor) {
        log.info(
            "No fsm transition for event {} moving from {} to {} found. Gracefully ignoring",
            context.getCausedEvent(), context.getFrom(), context.getTo());
        return;
      }
      processor.process(context);
      log.info("Processed fsm transition for event {} moving from {} to {}",
          context.getCausedEvent(), context.getFrom(), context.getTo());
    };
  }

  private void addTransition(final E event, final S from, final S to) {
    stateEngine.addTransition(from, new Transition<>(event, from, to));
  }

  public StateMachine<S, E, K, C> onTransition(final E event, final Collection<S> fromStates,
      final S to) {
    fromStates.forEach(state -> addTransition(event, state, to));
    return this;
  }

  public StateMachine<S, E, K, C> onError(final ErrorAction<C> action) { // Changed generic type
    this.errorAction = action; // Store it if needed, or directly pass to stateEngine if preferred
    stateEngine.addErrorAction(action);
    return this;
  }

  public StateMachine<S, E, K, C> onTransition(final E event, final S from, final S to) {
    addTransition(event, from, to);
    return this;
  }

  public StateMachine<S, E, K, C> end(final Collection<S> endStates) {
    stateEngine.addEndStates(endStates);
    return this;
  }

  @SneakyThrows
  public void start() {
    Preconditions.checkNotNull(stateEngine, "State machine can't be null");
    this.stateEngine.validate();
    // Register the default eventAction for any state transition if no specific one is added later for ANY_STATE_TRANSITION
    // This ensures the logging and processor hub invocation happens if not overridden.
    this.stateEngine.addAnyStateTransitionAction(this.eventAction);
    // Register the default or provided error action.
    this.stateEngine.addErrorAction(this.errorAction);
  }

  /**
   * Registers an action to be executed before any state transition occurs.
   * @param action The action to execute.
   * @return The StateMachine instance for fluent chaining.
   */
  public StateMachine<S, E, K, C> onBeforeAnyTransition(final BeforeStateTransitionAction<S, E, K, C> action) {
    stateEngine.addBeforeAnyTransitionAction(action);
    return this;
  }

  /**
   * Registers an action to be executed after any state transition occurs.
   * @param action The action to execute.
   * @return The StateMachine instance for fluent chaining.
   */
  public StateMachine<S, E, K, C> onAfterAnyTransition(final AfterStateTransitionAction<S, E, K, C> action) {
    stateEngine.addAfterAnyTransitionAction(action);
    return this;
  }

  /**
   * Registers an action to be executed before transitioning to a specific state.
   * @param to The target state.
   * @param action The action to execute.
   * @return The StateMachine instance for fluent chaining.
   */
  public StateMachine<S, E, K, C> onBeforeStateTransition(final S to, final BeforeStateTransitionAction<S, E, K, C> action) {
    stateEngine.addBeforeStateTransitionAction(to, action);
    return this;
  }

  /**
   * Registers an action to be executed after transitioning from a specific state.
   * @param from The source state.
   * @param action The action to execute.
   * @return The StateMachine instance for fluent chaining.
   */
  public StateMachine<S, E, K, C> onAfterStateTransition(final S from, final AfterStateTransitionAction<S, E, K, C> action) {
    stateEngine.addAfterStateTransitionAction(from, action);
    return this;
  }

  /**
   * Registers an action for a specific state transition (defined by current state and event).
   * This typically covers the main action for a transition.
   * @param from The source state.
   * @param event The event triggering the transition.
   * @param action The action to execute.
   * @return The StateMachine instance for fluent chaining.
   */
  public StateMachine<S, E, K, C> onStateTransition(final S from, final E event, final EventAction<E, S, K, C> action) {
    stateEngine.addStateTransitionAction(event, from, action); // Note: StateEngine has (event, state, action)
    return this;
  }

  /**
   * Registers an action to be executed for any state transition.
   * This is often used for generic logging or cross-cutting concerns.
   * Note: The default eventAction (logging and processor hub) is already registered for any transition during start().
   * Adding another action here will execute it in addition to the default one.
   * @param action The action to execute.
   * @return The StateMachine instance for fluent chaining.
   */
  public StateMachine<S, E, K, C> onAnyStateTransition(final EventAction<E, S, K, C> action) {
    stateEngine.addAnyStateTransitionAction(action);
    return this;
  }

  /**
   * Registers an action to be executed when the FSM reaches a specific final state.
   * @param finalState The final state.
   * @param action The action to execute.
   * @return The StateMachine instance for fluent chaining.
   */
  public StateMachine<S, E, K, C> onFinalState(final S finalState, final FinalStateAction<S, E, K, C> action) {
    stateEngine.addFinalStateAction(finalState, action);
    return this;
  }

  @SneakyThrows
  public void fireGrace(C context) {
    Preconditions.checkNotNull(stateEngine, "StateMachine core can't be null. It seems to have not been initiated or started");
    stateEngine
        .getTransition(context.getFrom(), context.getCausedEvent())
        .ifPresent(transition -> stateEngine.fire(context.getCausedEvent(), context));
  }

  @SneakyThrows
  public void fire(C context) {
    Preconditions.checkNotNull(stateEngine, "StateMachine core can't be null. It seems to have not been initiated or started");
    stateEngine
        .getTransition(context.getFrom(), context.getCausedEvent())
        .ifPresentOrElse(transition -> stateEngine.fire(context.getCausedEvent(), context),
            () -> {
              throw new IllegalArgumentException(
                  "Can't find a transition from " + context.getFrom() + " with event "
                      + context.getTo());
            });
  }

}
