import { computed, onScopeDispose, ref } from 'vue';
import { defineStore } from 'pinia';
import {
  GetSourceIssueAcknowledgementTargetsStateEnum as TargetState,
  SourceIssueControllerApi,
  type SourceIssueSummary,
  type SourceIssueTarget,
} from 'x8ing-mtl-api-typescript-fetch';
import { getApiConfiguration } from '@/utils/openApiClient';
import { readStorage, STORAGE_KEYS } from '@/utils/appStorage';
import { DIAGNOSTIC_AUTH_CHANGED } from '@/utils/diagnosticApiConfiguration';
import { acknowledgementMessage, plural } from '@/features/track-review/sourceIssuePresentation';
import { reviewError } from '@/features/track-review/api';
import { useApplicationStatusStore } from './applicationStatusStore';

export const sourceIssueApi = () => new SourceIssueControllerApi(getApiConfiguration());
const FEEDBACK_AVAILABLE_MS = 8_000;
export type SourceIssueScope = { index?: string; sourceId?: number };

export const useSourceIssueStore = defineStore('sourceIssues', () => {
  const status = useApplicationStatusStore();
  const busy = ref(false);
  const revision = ref(0);
  const sessionRevision = ref(0);
  const summary = ref<SourceIssueSummary | null>(null);
  const feedbackMessage = ref('');
  const feedbackError = ref('');
  const undoTargets = ref<SourceIssueTarget[]>([]);
  let feedbackTimer: ReturnType<typeof setTimeout> | undefined;
  const pendingFileCount = computed(() =>
    (status.processing?.indexers ?? []).reduce((count, index) => count + (index.pendingIssueFileCount ?? 0), 0)
  );
  const acknowledgedFileCount = computed(() =>
    (status.processing?.indexers ?? []).reduce((count, index) => count + (index.acknowledgedIssueFileCount ?? 0), 0)
  );

  function stopFeedbackTimer() {
    clearTimeout(feedbackTimer);
    feedbackTimer = undefined;
  }
  function dismissFeedback() {
    if (busy.value) return;
    stopFeedbackTimer();
    feedbackMessage.value = '';
    feedbackError.value = '';
    undoTargets.value = [];
  }
  function sessionGuard() {
    const session = sessionRevision.value;
    const token = readStorage(STORAGE_KEYS.jwt);
    return () => {
      if (session !== sessionRevision.value || token !== readStorage(STORAGE_KEYS.jwt))
        throw new Error('The sign-in changed. Refresh the current issues before continuing.');
    };
  }
  function authChanged() {
    sessionRevision.value++;
    revision.value++;
    summary.value = null;
    stopFeedbackTimer();
    feedbackMessage.value = '';
    feedbackError.value = '';
    undoTargets.value = [];
  }
  if (typeof window !== 'undefined') window.addEventListener(DIAGNOSTIC_AUTH_CHANGED, authChanged);
  onScopeDispose(() => {
    stopFeedbackTimer();
    if (typeof window !== 'undefined') window.removeEventListener(DIAGNOSTIC_AUTH_CHANGED, authChanged);
  });

  async function mutate(targets: readonly SourceIssueTarget[], reopen: boolean, assertSession: () => void) {
    assertSession();
    const request = { sourceIssueMutationRequest: { targets: targets.map((target) => ({ ...target })) } };
    const result = await (reopen
      ? sourceIssueApi().reopenSourceIssues(request)
      : sourceIssueApi().acknowledgeSourceIssues(request));
    assertSession();
    summary.value = result.summary ?? null;
    if (result.summary) status.applyIssueSummary(result.summary);
    stopFeedbackTimer();
    feedbackError.value = '';
    feedbackMessage.value = reopen
      ? result.reopened
        ? `Reopened ${plural(result.reopened, 'issue')}.${result.staleOrResolved ? ' Changed or resolved issues were left unchanged.' : ''}`
        : 'These issues changed or were resolved. Review the current issues.'
      : acknowledgementMessage(result);
    undoTargets.value = reopen ? [] : (result.changedTargets ?? []).map((target) => ({ ...target }));
    feedbackTimer = setTimeout(() => {
      feedbackMessage.value = '';
      feedbackError.value = '';
      undoTargets.value = [];
      feedbackTimer = undefined;
    }, FEEDBACK_AVAILABLE_MS);
    revision.value++;
    return result;
  }

  async function exclusive<T>(action: (assertSession: () => void) => Promise<T>) {
    if (busy.value) throw new Error('An issue action is already being saved.');
    const assertSession = sessionGuard();
    busy.value = true;
    try {
      return await action(assertSession);
    } finally {
      busy.value = false;
    }
  }

  function save(targets: readonly SourceIssueTarget[], reopen = false) {
    return exclusive((assertSession) => mutate(targets, reopen, assertSession));
  }

  async function prepare(scope: SourceIssueScope = {}, state: TargetState = TargetState.Pending) {
    const assertSession = sessionGuard();
    const snapshot = await sourceIssueApi().getSourceIssueAcknowledgementTargets({ ...scope, state });
    assertSession();
    return { ...snapshot, targets: (snapshot.targets ?? []).map((target) => ({ ...target })) };
  }

  async function undo() {
    if (busy.value || !undoTargets.value.length) return;
    const targets = undoTargets.value.map((target) => ({ ...target }));
    const session = sessionRevision.value;
    stopFeedbackTimer();
    feedbackError.value = '';
    try {
      const result = await save(targets, true);
      if (session === sessionRevision.value && result.reopened && !result.staleOrResolved)
        feedbackMessage.value = 'Acknowledgement undone.';
    } catch (failure) {
      const text = await reviewError(failure);
      if (session === sessionRevision.value) feedbackError.value = text;
    }
  }

  return {
    busy,
    revision,
    sessionRevision,
    summary,
    pendingFileCount,
    acknowledgedFileCount,
    feedbackMessage,
    feedbackError,
    undoTargets,
    save,
    prepare,
    undo,
    dismissFeedback,
  };
});
