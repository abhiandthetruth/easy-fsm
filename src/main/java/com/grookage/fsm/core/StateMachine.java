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
import com.grookage.fsm.core.action.DefaultErrorAction;
import com.grookage.fsm.core.engine.StateEngine;
import com.grookage.fsm.core.hubs.TransitionProcessorHub;
import com.grookage.fsm.core.models.entities.*;
import com.grookage.fsm.core.models.executors.ErrorAction;
import com.grookage.fsm.core.models.executors.EventAction;
import com.grookage.fsm.core.services.ActionService;
import com.grookage.fsm.core.services.StateManagementService;
import com.grookage.fsm.core.services.TransitionService;
import lombok.Data;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Data
@Slf4j
@Getter
public class StateMachine<S extends State, E extends Event, K extends TransitionKey, C extends Context<S, E, K>> {

  private final String name;
  private final StateEngine<E, S, K, C> stateEngine;
  private final TransitionProcessorHub<S, E, K, C> transitionProcessorHub;
  private ErrorAction<E, S, K, C> errorAction; // Made non-final to allow modification if needed by onError
  private final EventAction<E, S, K, C> eventAction; // This is the main action for transition processing

  // New fields for action storage
  private List<EventAction<E, S, K, C>> beforeAnyTransitionActions = new ArrayList<>();
  private List<EventAction<E, S, K, C>> afterAnyTransitionActions = new ArrayList<>();
  private Map<S, List<EventAction<E, S, K, C>>> beforeStateTransitionActions = new HashMap<>(); // Keyed by the 'to' state
  private Map<S, List<EventAction<E, S, K, C>>> afterStateTransitionActions = new HashMap<>();  // Keyed by the 'from' state
  private Map<S, List<EventAction<E, S, K, C>>> finalStateActions = new HashMap<>();        // Keyed by the final state
  private Set<S> localEndStates = new HashSet<>();


  public StateMachine(
      final String name,
      final S startState,
      final TransitionProcessorHub<S, E, K, C> transitionProcessorHub,
      final ErrorAction<E, S, K, C> errorAction,
      final EventAction<E, S, K, C> eventAction
  ) {
    this.name = name.toUpperCase(Locale.ROOT);
    this.stateEngine = new StateEngine<>(
        startState,
        new TransitionService<>(),
        new StateManagementService<>(),
        new ActionService<>()
    );
    this.transitionProcessorHub = transitionProcessorHub;
    this.errorAction = null != errorAction ? errorAction : new DefaultErrorAction<>();
    this.eventAction = null != eventAction ? eventAction : context -> {
      log.info("Processing fsm transition for event {} moving from {} to {}",
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

  public StateMachine<S, E, K, C> onError(final ErrorAction<E, S, K, C> action) {
    // Note: The original design sets errorAction in StateEngine directly.
    // To keep StateEngine unchanged, we might need to manage error actions here too,
    // or this onError simply configures the one passed to StateEngine.
    // For now, assuming it configures the main errorAction if needed, or adds to a list if multiple.
    // The original implementation calls stateEngine.addError, we keep that.
    this.errorAction = action; // if we want to replace the default one
    stateEngine.addError(action); // Keep original behavior
    return this;
  }

  public StateMachine<S, E, K, C> onTransition(final E event, final S from, final S to) {
    addTransition(event, from, to);
    return this;
  }

  // New public registration methods
  public StateMachine<S, E, K, C> onBeforeAnyTransition(EventAction<E, S, K, C> action) {
    this.beforeAnyTransitionActions.add(action);
    return this;
  }

  public StateMachine<S, E, K, C> onAfterAnyTransition(EventAction<E, S, K, C> action) {
    this.afterAnyTransitionActions.add(action);
    return this;
  }

  public StateMachine<S, E, K, C> onBeforeStateTransition(S toState, EventAction<E, S, K, C> action) {
    this.beforeStateTransitionActions.computeIfAbsent(toState, k -> new ArrayList<>()).add(action);
    return this;
  }

  public StateMachine<S, E, K, C> onAfterStateTransition(S fromState, EventAction<E, S, K, C> action) {
    this.afterStateTransitionActions.computeIfAbsent(fromState, k -> new ArrayList<>()).add(action);
    return this;
  }

  public StateMachine<S, E, K, C> onFinalState(S finalState, EventAction<E, S, K, C> action) {
    this.finalStateActions.computeIfAbsent(finalState, k -> new ArrayList<>()).add(action);
    return this;
  }

  public StateMachine<S, E, K, C> end(final Collection<S> endStates) {
    stateEngine.addEndStates(endStates);
    this.localEndStates.addAll(endStates); // Populate localEndStates
    return this;
  }

  @SneakyThrows
  public void start() {
    Preconditions.checkNotNull(stateEngine, "State machine can't be null");
    this.stateEngine.validate();
    this.stateEngine.anyTransition(this.eventAction);
    this.stateEngine.addError(this.errorAction);
  }

  @SneakyThrows
  public void fireGrace(C context) {
    Preconditions.checkNotNull(stateEngine, "StateMachine core can't be null. It seems to have not been initiated or started");
    S fromState = context.getFrom();
    E event = context.getCausedEvent();
    Transition<E, S> actualTransition = stateEngine.getTransition(fromState, event).orElse(null);

    // Call BEFORE_ANY_TRANSITION actions
    for (EventAction<E, S, K, C> action : beforeAnyTransitionActions) {
      action.call(context);
    }

    if (actualTransition == null) {
      // In fireGrace, we don't throw. We just don't proceed if no transition.
      log.warn("No transition found from {} with event {}. fireGrace will not proceed.", fromState, event);
      return;
    }

    S toState = actualTransition.getTo();
    boolean originalToStateNotSet = (context.getTo() == null);
    if (originalToStateNotSet) {
        context.setTo(toState);
    }

    // Call BEFORE_STATE_TRANSITION actions for the specific 'toState'
    for (EventAction<E, S, K, C> action : beforeStateTransitionActions.getOrDefault(toState, Collections.emptyList())) {
        action.call(context);
    }

    // Core state engine transition
    stateEngine.fire(event, context); // This will execute the main eventAction via ActionService

    // Post-transition actions
    // context.setFrom(fromState); // fromState is already correct
    // context.setTo(toState); // toState was set

    for (EventAction<E, S, K, C> action : afterStateTransitionActions.getOrDefault(fromState, Collections.emptyList())) {
        action.call(context);
    }

    for (EventAction<E, S, K, C> action : afterAnyTransitionActions) {
        action.call(context);
    }

    if (this.localEndStates.contains(toState)) {
        for (EventAction<E, S, K, C> action : finalStateActions.getOrDefault(toState, Collections.emptyList())) {
            action.call(context);
        }
    }
    
    if (originalToStateNotSet) {
        context.setTo(null); 
    }
  }

  @SneakyThrows
  public void fire(C context) {
    Preconditions.checkNotNull(stateEngine, "StateMachine core can't be null. It seems to have not been initiated or started");
    S fromState = context.getFrom();
    E event = context.getCausedEvent();
    Transition<E, S> actualTransition = stateEngine.getTransition(fromState, event).orElse(null);

    // Call BEFORE_ANY_TRANSITION actions
    for (EventAction<E, S, K, C> action : beforeAnyTransitionActions) {
        action.call(context);
    }

    if (actualTransition == null) {
        throw new IllegalArgumentException("Can't find a transition from " + fromState + " with event " + event);
    }

    S toState = actualTransition.getTo();
    boolean originalToStateNotSet = (context.getTo() == null);
    if (originalToStateNotSet) {
        context.setTo(toState);
    }

    // Call BEFORE_STATE_TRANSITION actions for the specific 'toState'
    for (EventAction<E, S, K, C> action : beforeStateTransitionActions.getOrDefault(toState, Collections.emptyList())) {
        action.call(context);
    }

    // Core state engine transition
    stateEngine.fire(event, context); // This will execute the main eventAction via ActionService

    // Post-transition actions
    // context.setFrom(fromState); // fromState is already correct
    // context.setTo(toState); // toState was set

    for (EventAction<E, S, K, C> action : afterStateTransitionActions.getOrDefault(fromState, Collections.emptyList())) {
        action.call(context);
    }

    for (EventAction<E, S, K, C> action : afterAnyTransitionActions) {
        action.call(context);
    }

    if (this.localEndStates.contains(toState)) {
        for (EventAction<E, S, K, C> action : finalStateActions.getOrDefault(toState, Collections.emptyList())) {
            action.call(context);
        }
    }
    
    if (originalToStateNotSet) {
        context.setTo(null); 
    }
  }
}
