<template>
  <BottomSheet
    :model-value="visible"
    :title="`Set up ${GOOGLE_HEALTH_FEATURE_LABEL}`"
    :detents="[
      { id: 'comfortable', height: 'min(820px, 92vh)' },
      { id: 'large', height: '98vh' },
    ]"
    initial-detent="comfortable"
    viewport-centered
    :no-backdrop="false"
    :no-scroll-hint="activeStep === INTRODUCTION_STEP || activeStep === SERVER_CONFIGURATION_STEP"
    scroll-hint-label="More setup instructions"
    sheet-class="google-health-setup-sheet"
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="gh-setup-layout">
      <nav class="gh-setup-steps" aria-label="Google Health setup steps">
        <div class="gh-setup-progress">{{ completedStepCount }} of {{ setupStepCount }} complete</div>
        <button
          v-for="(step, stepIndex) in setupSteps"
          :key="step.id"
          type="button"
          :class="[
            'gh-setup-step',
            {
              'gh-setup-step--active':
                activeStep === stepIndex + 1 || (activeStep === INTRODUCTION_STEP && stepIndex === 0),
            },
          ]"
          :aria-current="activeStep === stepIndex + 1 ? 'step' : undefined"
          @click="setActiveStep(stepIndex + 1)"
        >
          <span
            :class="[
              'gh-setup-step__number',
              {
                'gh-setup-step__number--done': stepComplete(stepIndex + 1),
                'gh-setup-step__number--active':
                  activeStep === stepIndex + 1 || (activeStep === INTRODUCTION_STEP && stepIndex === 0),
              },
            ]"
          >
            <i v-if="stepComplete(stepIndex + 1)" class="bi bi-check" aria-hidden="true" />
            <template v-else>{{ stepIndex + 1 }}</template>
          </span>
          <span class="gh-setup-step__label">{{ step.navigationTitle ?? step.title }}</span>
        </button>
      </nav>

      <main ref="setupContentRef" class="gh-setup-content">
        <header class="gh-setup-heading">
          <span v-if="activeStep > INTRODUCTION_STEP">Step {{ activeStep }} of {{ setupStepCount }}</span>
          <h3>{{ currentStep.title }}</h3>
          <p v-if="activeStep > INTRODUCTION_STEP">{{ currentStep.description }}</p>
        </header>

        <div v-if="oauthNotice" :class="['admin-message', `admin-message--${oauthNotice.tone}`]" role="status">
          <i :class="oauthNotice.icon" aria-hidden="true" />
          <span>{{ oauthNotice.text }}</span>
        </div>
        <div v-if="setupError" class="admin-message admin-message--error" role="alert">
          <i class="bi bi-exclamation-triangle" aria-hidden="true" />
          <span>{{ setupError }}</span>
        </div>

        <section v-if="activeStep === INTRODUCTION_STEP" class="gh-setup-section gh-setup-overview">
          <p>{{ GOOGLE_HEALTH_BETA_NOTICE }}</p>
          <div class="gh-setup-privacy" aria-label="Direct Google Health data path">
            <span class="gh-setup-privacy__icon" aria-hidden="true"><i class="bi bi-shield-check" /></span>
            <div class="gh-setup-privacy__body">
              <strong>Private by design</strong>
              <p>Your health data goes directly from Google Health to this MTL Explorer installation.</p>
              <p>No shared MTL Explorer service handles or inspects it.</p>
              <span class="gh-setup-privacy__path">
                Google Health <i class="bi bi-arrow-right" aria-hidden="true" /> Your MTL Explorer
              </span>
              <small>A direct connection requires installation-specific Google Cloud and OAuth setup.</small>
            </div>
          </div>

          <div class="gh-setup-overview-grid">
            <article class="gh-setup-panel">
              <h4>What you'll need</h4>
              <ul class="gh-setup-icon-list">
                <li>
                  <i class="bi bi-cloud" aria-hidden="true" />
                  <a :href="GOOGLE_CLOUD_CONSOLE_URL" target="_blank" rel="noopener noreferrer">
                    Google Cloud project
                  </a>
                </li>
                <li><i class="bi bi-person" aria-hidden="true" /><span>Google account</span></li>
                <li><i class="bi bi-server" aria-hidden="true" /><span>Docker host access</span></li>
              </ul>
            </article>

            <article class="gh-setup-panel">
              <h4>What happens next</h4>
              <ol class="gh-setup-timeline">
                <li><span>1</span>Configure Google Cloud</li>
                <li><span>2</span>Add credentials</li>
                <li><span>3</span>Connect and verify</li>
              </ol>
              <p class="gh-setup-testing-note">
                <i class="bi bi-calendar-week" aria-hidden="true" />
                Testing-mode authorization expires after seven days. Reauthorization repeats only the final consent
                step; existing mirrored data is kept.
              </p>
            </article>
          </div>
        </section>

        <section v-else-if="activeStep === PROJECT_AND_API_STEP" class="gh-setup-section">
          <div :class="['gh-setup-instruction-grid', { 'gh-setup-instruction-grid--with-image': enableApiScreenshot }]">
            <div class="gh-setup-task-list">
              <article class="gh-setup-task-card">
                <span class="gh-setup-task-card__number">1</span>
                <div>
                  <h4>Select a project</h4>
                  <p>Open Google Cloud and select an existing project or create a new one.</p>
                </div>
              </article>
              <a
                class="gh-setup-external-action"
                :href="GOOGLE_CLOUD_CONSOLE_URL"
                target="_blank"
                rel="noopener noreferrer"
              >
                Open Google Cloud Console <i class="bi bi-box-arrow-up-right" aria-hidden="true" />
              </a>
              <article class="gh-setup-task-card">
                <span class="gh-setup-task-card__number">2</span>
                <div>
                  <h4>Enable the API</h4>
                  <p>Enable <strong>Google Health API</strong> for the selected project.</p>
                </div>
              </article>
              <a
                class="gh-setup-external-action"
                :href="GOOGLE_HEALTH_API_URL"
                target="_blank"
                rel="noopener noreferrer"
              >
                Open Google Health API <i class="bi bi-box-arrow-up-right" aria-hidden="true" />
              </a>
            </div>
            <figure v-if="enableApiScreenshot" class="gh-setup-screenshot">
              <button
                type="button"
                class="gh-setup-screenshot__button"
                aria-haspopup="dialog"
                :aria-label="`Open ${enableApiScreenshot.alt} larger`"
                @click="openScreenshot($event, enableApiScreenshot)"
              >
                <img :src="enableApiScreenshot.src" :alt="enableApiScreenshot.alt" loading="lazy" />
                <span class="gh-setup-screenshot__action">
                  <i class="bi bi-arrows-angle-expand" aria-hidden="true" /> View larger
                </span>
              </button>
              <figcaption>{{ enableApiScreenshot.caption }}</figcaption>
            </figure>
          </div>
        </section>

        <section v-else-if="activeStep === BRANDING_STEP" class="gh-setup-section">
          <div class="gh-setup-inline-note" role="status">
            <i class="bi bi-palette" aria-hidden="true" />
            <span>
              <strong>Create the Google Auth Platform app once.</strong>
              Keep the same Google Cloud project selected throughout the setup.
            </span>
          </div>
          <div class="gh-setup-task-list gh-setup-task-list--compact">
            <article class="gh-setup-task-card">
              <span class="gh-setup-task-card__number">1</span>
              <div>
                <h4>Open Google Auth Platform</h4>
                <p>Select <strong>Get started</strong> if Google has not created an app configuration yet.</p>
              </div>
            </article>
            <article class="gh-setup-task-card">
              <span class="gh-setup-task-card__number">2</span>
              <div>
                <h4>Enter app information</h4>
                <p>Use <code>MTL Explorer</code> as the app name and select your email for user support.</p>
              </div>
            </article>
            <article class="gh-setup-task-card">
              <span class="gh-setup-task-card__number">3</span>
              <div>
                <h4>Finish the initial setup</h4>
                <p>
                  Select <strong>External</strong>, add your contact email, accept the user-data policy, and create the
                  app configuration.
                </p>
              </div>
            </article>
          </div>
          <a
            class="gh-setup-external-action"
            :href="GOOGLE_AUTH_OVERVIEW_URL"
            target="_blank"
            rel="noopener noreferrer"
          >
            Open Google Auth Platform <i class="bi bi-box-arrow-up-right" aria-hidden="true" />
          </a>
          <div v-if="authPlatformScreenshot" class="gh-setup-instruction-grid gh-setup-instruction-grid--with-image">
            <article class="gh-setup-panel gh-setup-panel--instructions">
              <h4>After creating the app</h4>
              <p>
                Open <strong>Audience</strong>. You should see <strong>Testing</strong> and <strong>External</strong>.
                The next step adds your Google Health account as a test user.
              </p>
            </article>
            <figure class="gh-setup-screenshot">
              <button
                type="button"
                class="gh-setup-screenshot__button"
                aria-haspopup="dialog"
                :aria-label="`Open ${authPlatformScreenshot.alt} larger`"
                @click="openScreenshot($event, authPlatformScreenshot)"
              >
                <img :src="authPlatformScreenshot.src" :alt="authPlatformScreenshot.alt" loading="lazy" />
                <span class="gh-setup-screenshot__action">
                  <i class="bi bi-arrows-angle-expand" aria-hidden="true" /> View larger
                </span>
              </button>
              <figcaption>{{ authPlatformScreenshot.caption }}</figcaption>
            </figure>
          </div>
        </section>

        <section v-else-if="activeStep === TEST_USER_STEP" class="gh-setup-section">
          <div class="gh-setup-inline-note" role="status">
            <i class="bi bi-person-check" aria-hidden="true" />
            <span>
              Google authorizes only accounts listed under <strong>Test users</strong> while the app is in Testing. Add
              the account that owns the health data. Skipping this can cause an <code>access_denied</code> error at
              Google consent. MTL Explorer cannot check your project's test-user list.
            </span>
          </div>
          <div class="gh-setup-task-list gh-setup-task-list--compact">
            <article class="gh-setup-task-card">
              <span class="gh-setup-task-card__number">1</span>
              <div>
                <h4>Check the audience</h4>
                <p>Open <strong>Audience</strong>. Confirm <strong>Testing</strong> and <strong>External</strong>.</p>
              </div>
            </article>
            <article class="gh-setup-task-card">
              <span class="gh-setup-task-card__number">2</span>
              <div>
                <h4>Add the account</h4>
                <p>
                  Under <strong>Test users</strong>, select <strong>+ Add users</strong> and enter the Google account
                  that owns the health data.
                </p>
              </div>
            </article>
            <article class="gh-setup-task-card">
              <span class="gh-setup-task-card__number">3</span>
              <div>
                <h4>Save and verify</h4>
                <p>Select <strong>Save</strong> and confirm the account appears in the Test users list.</p>
              </div>
            </article>
          </div>
          <a
            class="gh-setup-external-action"
            :href="GOOGLE_AUTH_AUDIENCE_URL"
            target="_blank"
            rel="noopener noreferrer"
          >
            Open Audience <i class="bi bi-box-arrow-up-right" aria-hidden="true" />
          </a>
          <div class="gh-setup-inline-note gh-setup-inline-note--warning" role="status">
            <i class="bi bi-exclamation-triangle" aria-hidden="true" />
            <span>
              Google warns that the app is unverified, and access expires after seven days. Both are expected for this
              personal setup.
            </span>
          </div>
          <details class="gh-setup-alternative">
            <summary>Why use Testing mode?</summary>
            <div>
              <p>
                Testing is the setup MTL Explorer supports for a personal installation. You don't need to publish the
                app or have it verified by Google. Add yourself as a test user and reconnect once a week.
              </p>
              <p>
                With Google Workspace, an Internal audience may avoid the weekly reconnect. This is an advanced option
                for organizations, not the normal home setup.
              </p>
            </div>
          </details>
        </section>

        <section v-else-if="activeStep === OAUTH_CLIENT_STEP" class="gh-setup-section">
          <div :class="['gh-setup-inline-note', { 'gh-setup-inline-note--warning': !manualMode }]" role="status">
            <i :class="manualMode ? 'bi bi-pc-display' : 'bi bi-window'" aria-hidden="true" />
            <span v-if="manualMode">
              <strong>Choose Desktop app.</strong>
              This is the client type used by Google's own Health CLI for a loopback authorization flow.
            </span>
            <span v-else>
              <strong>This installation uses the advanced proxy callback.</strong>
              Register the exact MTL Explorer address shown below.
            </span>
          </div>
          <div
            :class="[
              'gh-setup-instruction-grid',
              { 'gh-setup-instruction-grid--with-image': !manualMode && oauthClientScreenshot },
            ]"
          >
            <article class="gh-setup-panel gh-setup-panel--instructions">
              <h4>Create the OAuth client</h4>
              <ol class="gh-setup-numbered-list">
                <li>
                  <span>1</span>
                  <div>Open <strong>Clients</strong> and select <strong>Create client</strong>.</div>
                </li>
                <li>
                  <span>2</span>
                  <div v-if="manualMode">Select <strong>Desktop app</strong>.</div>
                  <div v-else>Select <strong>Web application</strong>.</div>
                </li>
                <li>
                  <span>3</span>
                  <div>Enter <code>MTL Explorer</code> as the name.</div>
                </li>
                <li v-if="!manualMode">
                  <span>4</span>
                  <div>Leave <strong>Authorized JavaScript origins</strong> empty.</div>
                </li>
                <li v-if="!manualMode">
                  <span>5</span>
                  <div>
                    Under <strong>Authorized redirect URIs</strong>, select <strong>+ Add URI</strong> and enter this
                    exact address.
                  </div>
                </li>
                <li>
                  <span>{{ manualMode ? 4 : 6 }}</span>
                  <div>Select <strong>Create</strong>.</div>
                </li>
                <li>
                  <span>{{ manualMode ? 5 : 7 }}</span>
                  <div>Select <strong>Download JSON</strong> and save the credentials file.</div>
                </li>
              </ol>
              <div v-if="!manualMode" class="gh-setup-copy-block">
                <code>{{ oauthClientRedirectUri }}</code>
                <Button
                  :label="copiedValue === 'redirect' ? 'Copied' : 'Copy redirect URI'"
                  :icon="copiedValue === 'redirect' ? 'bi bi-clipboard-check' : 'bi bi-copy'"
                  size="small"
                  severity="secondary"
                  @click="copyValue(oauthClientRedirectUri, 'redirect')"
                />
              </div>
              <p v-if="manualMode" class="gh-setup-note">
                Desktop clients do not need an Authorized JavaScript origin or Authorized redirect URI.
              </p>
              <div class="gh-setup-inline-note gh-setup-inline-note--warning gh-setup-credentials-note" role="note">
                <i class="bi bi-exclamation-triangle" aria-hidden="true" />
                <span>
                  <strong class="gh-setup-credentials-note__title">Save your credentials before continuing</strong>
                  Download the JSON file and save both the <strong>Client ID</strong> and
                  <strong>Client secret</strong> before closing Google's dialog. Both values are required later in
                  <strong>Server configuration</strong>.
                </span>
              </div>
              <a
                class="gh-setup-external-action"
                :href="GOOGLE_AUTH_CLIENTS_URL"
                target="_blank"
                rel="noopener noreferrer"
              >
                Open OAuth Clients <i class="bi bi-box-arrow-up-right" aria-hidden="true" />
              </a>
            </article>
            <figure v-if="!manualMode && oauthClientScreenshot" class="gh-setup-screenshot">
              <button
                type="button"
                class="gh-setup-screenshot__button"
                aria-haspopup="dialog"
                :aria-label="`Open ${oauthClientScreenshot.alt} larger`"
                @click="openScreenshot($event, oauthClientScreenshot)"
              >
                <img :src="oauthClientScreenshot.src" :alt="oauthClientScreenshot.alt" loading="lazy" />
                <span class="gh-setup-screenshot__action">
                  <i class="bi bi-arrows-angle-expand" aria-hidden="true" /> View larger
                </span>
              </button>
              <figcaption>{{ oauthClientScreenshot.caption }}</figcaption>
            </figure>
          </div>
        </section>

        <section v-else-if="activeStep === DATA_ACCESS_STEP" class="gh-setup-section">
          <div class="gh-setup-split">
            <article class="gh-setup-panel gh-setup-panel--instructions">
              <h4>Configure Data Access</h4>
              <ol class="gh-setup-numbered-list">
                <li>
                  <span>1</span>
                  <div>Open <strong>Data Access</strong> and select <strong>Add or remove scopes</strong>.</div>
                </li>
                <li>
                  <span>2</span>
                  <div>Filter for <strong>Google Health API</strong>.</div>
                </li>
                <li>
                  <span>3</span>
                  <div>Select the scopes below, choose <strong>Update</strong>, then choose <strong>Save</strong>.</div>
                </li>
              </ol>
              <a
                class="gh-setup-external-action"
                :href="GOOGLE_AUTH_SCOPES_URL"
                target="_blank"
                rel="noopener noreferrer"
              >
                Open Data Access <i class="bi bi-box-arrow-up-right" aria-hidden="true" />
              </a>
              <figure v-if="dataAccessScreenshot" class="gh-setup-screenshot">
                <button
                  type="button"
                  class="gh-setup-screenshot__button"
                  aria-haspopup="dialog"
                  :aria-label="`Open ${dataAccessScreenshot.alt} larger`"
                  @click="openScreenshot($event, dataAccessScreenshot)"
                >
                  <img :src="dataAccessScreenshot.src" :alt="dataAccessScreenshot.alt" loading="lazy" />
                  <span class="gh-setup-screenshot__action">
                    <i class="bi bi-arrows-angle-expand" aria-hidden="true" /> View larger
                  </span>
                </button>
                <figcaption>{{ dataAccessScreenshot.caption }}</figcaption>
              </figure>
            </article>

            <article class="gh-setup-panel gh-setup-panel--scopes">
              <div class="gh-setup-scope-group">
                <strong>Required</strong>
                <code v-for="scope in requiredScopes" :key="scope">{{ scope }}</code>
              </div>
              <div v-if="locationScope" class="gh-setup-scope-group">
                <strong>Optional for exercise routes</strong>
                <code>{{ locationScope }}</code>
              </div>
              <Button
                class="gh-setup-panel-action"
                :label="copiedValue === 'scopes' ? 'Copied' : 'Copy all four scopes'"
                :icon="copiedValue === 'scopes' ? 'bi bi-clipboard-check' : 'bi bi-copy'"
                size="small"
                severity="secondary"
                @click="copyValue(requestedScopes.join('\n'), 'scopes')"
              />
              <div class="gh-setup-inline-note" role="note">
                <i class="bi bi-lightbulb" aria-hidden="true" />
                <span>
                  <strong>Tip:</strong> Select <strong>Copy all four scopes</strong>, then paste them into
                  <strong>Manually add scopes</strong> in Google's <strong>Add or remove scopes</strong> panel. Select
                  <strong>Add to table</strong>, then <strong>Update</strong>, then <strong>Save</strong>.
                </span>
              </div>
            </article>
          </div>
        </section>

        <section v-else-if="activeStep === SERVER_CONFIGURATION_STEP" class="gh-setup-section">
          <div class="gh-setup-server-grid">
            <div class="gh-setup-server-tasks">
              <article class="gh-setup-server-task">
                <span class="gh-setup-task-card__number">1</span>
                <div class="gh-setup-server-task__body">
                  <h4>Open the Compose environment</h4>
                  <p>Create or open <code>.env</code> beside <code>docker-compose.yml</code>.</p>
                  <span class="gh-setup-file-location">
                    <i class="bi bi-folder" aria-hidden="true" /> docker-compose.yml &nbsp; + &nbsp; .env
                  </span>
                </div>
              </article>

              <article class="gh-setup-server-task">
                <span class="gh-setup-task-card__number">2</span>
                <div class="gh-setup-server-task__body">
                  <h4>Add credentials</h4>
                  <div class="gh-setup-copy-block gh-setup-copy-block--environment">
                    <pre>{{ environmentTemplate }}</pre>
                    <Button
                      :label="copiedValue === 'environment' ? 'Copied' : 'Copy settings'"
                      :icon="copiedValue === 'environment' ? 'bi bi-clipboard-check' : 'bi bi-copy'"
                      size="small"
                      severity="secondary"
                      @click="copyValue(environmentTemplate, 'environment')"
                    />
                  </div>
                </div>
              </article>

              <article class="gh-setup-server-task">
                <span class="gh-setup-task-card__number">3</span>
                <div class="gh-setup-server-task__body">
                  <h4>Apply the configuration</h4>
                  <div class="gh-setup-copy-block">
                    <code>{{ COMPOSE_APPLY_COMMAND }}</code>
                    <Button
                      :label="copiedValue === 'compose' ? 'Copied' : 'Copy command'"
                      :icon="copiedValue === 'compose' ? 'bi bi-clipboard-check' : 'bi bi-copy'"
                      size="small"
                      severity="secondary"
                      @click="copyValue(COMPOSE_APPLY_COMMAND, 'compose')"
                    />
                  </div>
                  <p class="gh-setup-note">
                    Use <code>docker compose up -d</code>, not only <code>docker compose restart</code>. A plain restart
                    keeps the old environment.
                  </p>
                </div>
              </article>
            </div>

            <aside :class="['gh-setup-checks', { 'gh-setup-checks--success': serverConfigured }]">
              <h4>Server checks</h4>
              <div v-if="serverConfigured" class="gh-setup-check-row gh-setup-check-row--success" role="status">
                <i class="bi bi-check-circle" aria-hidden="true" />
                <span>Configuration complete</span>
              </div>
              <template v-else>
                <div v-for="issue in configurationIssues" :key="issue" class="gh-setup-check-row">
                  <i class="bi bi-exclamation-circle" aria-hidden="true" />
                  <span>{{ configurationIssueLabel(issue) }}</span>
                </div>
              </template>
              <p>
                {{
                  serverConfigured
                    ? 'The server is ready for the Google connection.'
                    : 'Apply the environment, wait for MTL Explorer to restart, then run the checks again.'
                }}
              </p>
              <Button
                class="gh-setup-checks__action"
                label="Check again"
                icon="pi pi-refresh"
                size="small"
                :severity="serverConfigured ? 'secondary' : undefined"
                :loading="refreshing"
                @click="emit('refresh')"
              />
            </aside>
          </div>
        </section>

        <section v-else class="gh-setup-section">
          <template v-if="connected">
            <div :class="['gh-setup-connected', `gh-setup-connected--${initialSyncStatus.tone}`]" role="status">
              <span class="gh-setup-connected__icon"><i :class="initialSyncStatus.icon" aria-hidden="true" /></span>
              <div>
                <h4>{{ initialSyncStatus.text }}</h4>
                <p>{{ initialSyncDetail }}</p>
              </div>
            </div>
            <GoogleHealthSyncProgress :status="status" :count-animation="countAnimation" show-history />
            <div class="gh-setup-split">
              <article v-if="authorizationExpiry" class="gh-setup-panel">
                <h4><i class="bi bi-calendar-week" aria-hidden="true" /> Authorization</h4>
                <p>
                  This Google authorization expires on <strong>{{ formatDate(authorizationExpiry) }}</strong
                  >. MTL Explorer will ask for reauthorization before or after that date.
                </p>
              </article>
              <article v-if="grantedCategories.length" class="gh-setup-panel gh-setup-grants">
                <h4>Granted read access</h4>
                <div>
                  <span v-for="category in grantedCategories" :key="category" class="selection-chip">
                    {{ category }}
                  </span>
                </div>
                <p v-if="!locationGranted" class="gh-setup-note">
                  Location access was not granted. Health records can sync, but exercise routes cannot be imported.
                </p>
              </article>
            </div>
            <div class="gh-setup-final-actions">
              <Button label="Open Health" icon="bi bi-heart-pulse" @click="emit('open-health')" />
              <Button label="Close" severity="secondary" @click="emit('update:visible', false)" />
            </div>
          </template>
          <template v-else>
            <div v-if="!serverConfigured" class="gh-setup-inline-note gh-setup-inline-note--warning">
              <i class="bi bi-sliders" aria-hidden="true" />
              <span>Complete the server configuration before connecting.</span>
            </div>
            <div v-if="consentScreenshot" class="gh-setup-consent-reference">
              <div class="gh-setup-consent-reference__copy">
                <h4>Review the final Google approval</h4>
                <p>
                  On Google's <strong>Select what this app can access</strong> screen, choose
                  <strong>Select all</strong>, confirm the check marks, then select <strong>Continue</strong>.
                </p>
                <p>
                  GPS location is optional if you do not need exercise routes. Activity and fitness, health metrics, and
                  sleep are required.
                </p>
              </div>
              <figure class="gh-setup-screenshot gh-setup-screenshot--consent">
                <button
                  type="button"
                  class="gh-setup-screenshot__button"
                  aria-haspopup="dialog"
                  :aria-label="`Open ${consentScreenshot.alt} larger`"
                  @click="openScreenshot($event, consentScreenshot)"
                >
                  <img :src="consentScreenshot.src" :alt="consentScreenshot.alt" loading="lazy" />
                  <span class="gh-setup-screenshot__action">
                    <i class="bi bi-arrows-angle-expand" aria-hidden="true" /> View larger
                  </span>
                </button>
                <figcaption>{{ consentScreenshot.caption }}</figcaption>
              </figure>
            </div>
            <template v-if="manualMode">
              <div class="gh-setup-connect-grid">
                <article class="gh-setup-panel gh-setup-connect-instructions">
                  <h4>Authorize the connection</h4>
                  <ol class="gh-setup-numbered-list">
                    <li>
                      <span>1</span>
                      <div>
                        Select <strong>Open Google consent</strong>, sign in with the account you added under
                        <strong>Audience → Test users</strong>, and approve the requested read-only access.
                      </div>
                    </li>
                    <li>
                      <span>2</span>
                      <div>
                        Google opens <code>{{ manualRedirectUri }}</code
                        >. The browser's “can't reach this page” error is expected because no server is listening there.
                      </div>
                    </li>
                    <li>
                      <span>3</span>
                      <div>
                        Press <strong>Cmd/Ctrl+L</strong>, copy the complete address, and paste it here. Developer tools
                        and the error-page body are not needed.
                      </div>
                    </li>
                  </ol>
                  <Button
                    label="Open Google consent"
                    icon="bi bi-box-arrow-up-right"
                    :loading="connecting"
                    :disabled="!serverConfigured || connecting || completing"
                    @click="emit('connect')"
                  />
                </article>

                <article class="gh-setup-panel gh-setup-connect-form">
                  <label class="gh-setup-manual-paste">
                    <strong>Google return address</strong>
                    <span>Paste the whole address, including <code>code</code> and <code>state</code>.</span>
                    <textarea
                      v-model.trim="manualRedirectUrl"
                      rows="4"
                      autocomplete="off"
                      spellcheck="false"
                      :aria-invalid="Boolean(manualRedirectError)"
                      :aria-describedby="manualRedirectError ? 'gh-setup-manual-error' : undefined"
                      :placeholder="`${manualRedirectUri}?code=...&state=...`"
                    />
                    <small
                      v-if="manualRedirectError"
                      id="gh-setup-manual-error"
                      class="gh-setup-validation-error"
                      role="alert"
                      >{{ manualRedirectError }}</small
                    >
                  </label>
                  <p class="gh-setup-secure-code-note">
                    <i class="bi bi-shield-lock" aria-hidden="true" />
                    This address contains a short-lived, one-time code. Paste it only into this MTL Explorer and finish
                    within ten minutes.
                  </p>
                  <Button
                    label="Complete connection"
                    icon="bi bi-check-circle"
                    :loading="completing"
                    :disabled="!manualRedirectUrl || Boolean(manualRedirectError) || connecting || completing"
                    @click="submitManualCompletion"
                  />
                </article>
              </div>
            </template>
            <article v-else class="gh-setup-panel gh-setup-proxy-connect">
              <span><i class="bi bi-heart-pulse" aria-hidden="true" /></span>
              <h4>Connect Google Health</h4>
              <p>MTL Explorer will request the configured read-only categories.</p>
              <Button
                label="Connect Google"
                icon="pi pi-link"
                :loading="connecting"
                :disabled="!serverConfigured || connecting"
                @click="emit('connect')"
              />
            </article>
          </template>
        </section>

        <label v-if="activeStep > 0 && activeStep < SERVER_CONFIGURATION_STEP" class="gh-setup-complete">
          <input type="checkbox" :checked="stepComplete(activeStep)" @change="toggleCurrentStep" />
          {{
            activeStep === TEST_USER_STEP
              ? 'I added the Google Health account under Test users'
              : 'I completed this step'
          }}
        </label>
      </main>
    </div>

    <template #footer>
      <div class="gh-setup-footer">
        <Button
          label="Back"
          icon="pi pi-angle-left"
          severity="secondary"
          outlined
          :disabled="activeStep === 0"
          @click="setActiveStep(activeStep - 1)"
        />
        <Button
          v-if="activeStep < steps.length - 1"
          :label="activeStep === INTRODUCTION_STEP ? 'Start setup' : 'Next'"
          icon="pi pi-angle-right"
          icon-pos="right"
          :disabled="activeStep === TEST_USER_STEP && !stepComplete(TEST_USER_STEP)"
          @click="nextStep"
        />
      </div>
    </template>
  </BottomSheet>

  <Teleport to="body">
    <div
      v-if="expandedScreenshot"
      class="gh-setup-screenshot-preview"
      role="dialog"
      aria-modal="true"
      aria-labelledby="gh-setup-screenshot-preview-title"
      @click.self="closeScreenshot"
      @keydown="handleScreenshotPreviewKeydown"
    >
      <div class="gh-setup-screenshot-preview__panel">
        <header class="gh-setup-screenshot-preview__header">
          <div>
            <h3 id="gh-setup-screenshot-preview-title">Reference screenshot</h3>
            <p>{{ expandedScreenshot.caption }}</p>
          </div>
          <button
            ref="screenshotCloseRef"
            type="button"
            class="gh-setup-screenshot-preview__close"
            aria-label="Close screenshot"
            @click="closeScreenshot"
          >
            <i class="bi bi-x-lg" aria-hidden="true" />
          </button>
        </header>
        <img :src="expandedScreenshot.src" :alt="expandedScreenshot.alt" />
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import Button from 'primevue/button';
import BottomSheet from '@/components/ui/BottomSheet.vue';
import { backendBaseUrl } from '@/utils/apiBase';
import { writeTextToClipboard } from '@/utils/clipboard';
import type { CountAnimation } from '@/utils/countAnimation';
import GoogleHealthSyncProgress from '../../components/GoogleHealthSyncProgress.vue';
import type { GHSetupResponse, GHStatusResponse } from '../../api/googleHealthApi';
import { buildGoogleHealthRedirectUri, validateGoogleHealthManualRedirectUrl } from '../../utils/googleHealthSetup';
import { GOOGLE_HEALTH_BETA_NOTICE, GOOGLE_HEALTH_FEATURE_LABEL } from '../../utils/googleHealthPresentation';
import {
  loadGoogleHealthSetupProgress,
  reconcileGoogleHealthSetupRedirect,
  saveGoogleHealthSetupProgress,
  type GoogleHealthSetupProgress,
} from '../../utils/googleHealthSetupProgress';

type SetupStep = { id: string; title: string; navigationTitle?: string; description: string };
type SetupScreenshot = { src: string; alt: string; caption: string };

const INTRODUCTION_STEP = 0;
const PROJECT_AND_API_STEP = 1;
const BRANDING_STEP = 2;
const TEST_USER_STEP = 3;
const OAUTH_CLIENT_STEP = 4;
const DATA_ACCESS_STEP = 5;
const SERVER_CONFIGURATION_STEP = 6;
const CONNECTION_STEP = 7;
const COPY_FEEDBACK_MS = 2_000;
const GOOGLE_CLOUD_CONSOLE_URL = 'https://console.cloud.google.com/';
const GOOGLE_HEALTH_API_URL = 'https://console.cloud.google.com/apis/library/health.googleapis.com';
const GOOGLE_AUTH_OVERVIEW_URL = 'https://console.cloud.google.com/auth/overview';
const GOOGLE_AUTH_AUDIENCE_URL = 'https://console.cloud.google.com/auth/audience';
const GOOGLE_AUTH_SCOPES_URL = 'https://console.cloud.google.com/auth/scopes';
const GOOGLE_AUTH_CLIENTS_URL = 'https://console.cloud.google.com/auth/clients';
const COMPOSE_APPLY_COMMAND = 'docker compose up -d';
const DEFAULT_CALLBACK_PATH = '/api/google-health/oauth/callback';

const screenshotModules = import.meta.glob('../../../../assets/google-health-setup/*.webp', {
  eager: true,
  query: '?url',
  import: 'default',
}) as Record<string, string>;

const steps: SetupStep[] = [
  {
    id: 'introduction',
    title: 'Before you start',
    navigationTitle: 'Overview',
    description: 'Keep the project in Testing mode. You need to reconnect once a week.',
  },
  {
    id: 'project',
    title: 'Project and API',
    description: 'Create or select a Google Cloud project and enable Google Health API.',
  },
  {
    id: 'branding',
    title: 'App details',
    navigationTitle: 'Branding',
    description: 'Create the Google Auth Platform app configuration for MTL Explorer.',
  },
  {
    id: 'test-user',
    title: 'Add your test user',
    navigationTitle: 'Audience and test user',
    description: 'Keep Testing enabled and add the Google Health account as a test user.',
  },
  {
    id: 'client',
    title: 'OAuth client',
    navigationTitle: 'OAuth client',
    description: 'Create the OAuth client used by this installation.',
  },
  {
    id: 'scopes',
    title: 'Read-only scopes',
    navigationTitle: 'Data access',
    description: 'Configure the health categories MTL Explorer can read.',
  },
  {
    id: 'server',
    title: 'Server configuration',
    description: 'Add the Google OAuth values to the Docker Compose environment.',
  },
  {
    id: 'connect',
    title: 'Connect and verify',
    description: 'Approve access, copy the returned address, and start the local mirror.',
  },
];

const props = withDefaults(
  defineProps<{
    visible: boolean;
    setup: GHSetupResponse | null;
    status: GHStatusResponse | null;
    countAnimation?: CountAnimation | null;
    refreshing?: boolean;
    connecting?: boolean;
    completing?: boolean;
    oauthResult?: string;
    oauthMessage?: string;
    setupError?: string;
  }>(),
  {
    refreshing: false,
    connecting: false,
    completing: false,
    oauthResult: '',
    oauthMessage: '',
    setupError: '',
    countAnimation: undefined,
  }
);

const emit = defineEmits<{
  (event: 'update:visible', visible: boolean): void;
  (event: 'refresh'): void;
  (event: 'connect'): void;
  (event: 'complete', redirectUrl: string): void;
  (event: 'open-health'): void;
}>();

const storage = browserStorage();
const progress = ref<GoogleHealthSetupProgress>(
  storage ? loadGoogleHealthSetupProgress(storage) : { activeStep: 0, completedSteps: [], lastProposedRedirectUri: '' }
);
const copiedValue = ref('');
const manualRedirectUrl = ref('');
const setupContentRef = ref<HTMLElement | null>(null);
const expandedScreenshot = ref<SetupScreenshot | null>(null);
const screenshotCloseRef = ref<HTMLButtonElement | null>(null);
let screenshotTrigger: HTMLElement | null = null;
let copyFeedbackTimer: ReturnType<typeof setTimeout> | null = null;

const activeStep = computed(() => progress.value.activeStep);
const currentStep = computed(() => steps[activeStep.value]);
const setupSteps = steps.slice(1);
const setupStepCount = setupSteps.length;
const completedStepCount = computed(() => setupSteps.filter((_, stepIndex) => stepComplete(stepIndex + 1)).length);
const requestedScopes = computed(() => props.setup?.requestedScopes ?? []);
const locationScope = computed(() => requestedScopes.value.find((scope) => scope.endsWith('.location.readonly')) ?? '');
const requiredScopes = computed(() => requestedScopes.value.filter((scope) => scope !== locationScope.value));
const manualMode = computed(() => props.setup?.oauthMode !== 'PROXY');
const manualRedirectUri = computed(() => props.setup?.manualRedirectUri ?? 'http://127.0.0.1:1');
const manualRedirectError = computed(() =>
  manualRedirectUrl.value ? validateGoogleHealthManualRedirectUrl(manualRedirectUrl.value, manualRedirectUri.value) : ''
);
const proposedRedirectUri = computed(() =>
  buildGoogleHealthRedirectUri(
    backendBaseUrl,
    window.location.origin,
    props.setup?.callbackPath ?? DEFAULT_CALLBACK_PATH
  )
);
const oauthClientRedirectUri = computed(() => (manualMode.value ? manualRedirectUri.value : proposedRedirectUri.value));
const progressRedirectKey = computed(() => oauthClientRedirectUri.value);
const serverConfigured = computed(() => props.setup?.configurationState === 'CONFIGURED');
const configurationIssues = computed(() => props.setup?.configurationIssues ?? []);
const connected = computed(() => props.status?.connectionState === 'CONNECTED');
const authorizationExpiry = computed(() => props.status?.refreshTokenExpiresAt);
const enableApiScreenshot = setupScreenshot(
  '01-enable-api.webp',
  'Google Cloud showing Google Health API enabled',
  'The selected project should show API Enabled.'
);
const authPlatformScreenshot = setupScreenshot(
  '02-auth-platform.webp',
  'Google Auth Platform Audience page showing Testing, External, and an empty Test users list',
  'Audience after initial app setup, before adding your test user.'
);
const oauthClientScreenshot = setupScreenshot(
  '04-web-client.webp',
  'Google Cloud form for creating a Web application OAuth client',
  'Use Web application, leave JavaScript origins empty, and add the shown address under Authorized redirect URIs.'
);
const dataAccessScreenshot = setupScreenshot(
  '05-data-access-scopes.webp',
  'Google Auth Platform Data access page listing all four Google Health read-only scopes',
  'All four Google Health scopes should be listed. Select Save to apply them.'
);
const consentScreenshot = setupScreenshot(
  '06-google-consent.webp',
  'Google final approval screen with all four Google Health categories selected',
  'Select all four categories for health records and exercise routes, then select Continue.'
);
const environmentTemplate = computed(() => {
  const credentials =
    'MTL_GOOGLE_HEALTH_CLIENT_ID=<paste-client-id>\nMTL_GOOGLE_HEALTH_CLIENT_SECRET=<paste-client-secret>';
  return manualMode.value ? credentials : `${credentials}\nMTL_GOOGLE_HEALTH_REDIRECT_URI=${proposedRedirectUri.value}`;
});
const grantedCategories = computed(() => {
  const labels: Record<string, string> = {
    activityAndFitness: 'Activity and fitness',
    healthMetricsAndMeasurements: 'Health metrics',
    sleep: 'Sleep',
    location: 'Location',
  };
  return Object.entries(props.status?.grantedCategories ?? {})
    .filter(([, granted]) => granted)
    .map(([category]) => labels[category] ?? category);
});
const locationGranted = computed(() => props.status?.grantedCategories?.location === true);
const initialSyncStatus = computed(() => {
  const jobState = props.status?.job?.state ?? '';
  if (jobState === 'QUEUED' || jobState === 'RUNNING')
    return { tone: 'success', icon: 'bi bi-arrow-repeat', text: 'Connected. The first sync has started.' };
  if (jobState === 'FAILED' || jobState === 'COMPLETED_WITH_ERRORS')
    return {
      tone: 'warning',
      icon: 'bi bi-exclamation-triangle',
      text: 'Connected, but the latest sync needs attention.',
    };
  return { tone: 'success', icon: 'bi bi-check-circle', text: 'Google Health is connected.' };
});
const initialSyncDetail = computed(() => {
  const jobState = props.status?.job?.state ?? '';
  if (jobState === 'QUEUED' || jobState === 'RUNNING')
    return (
      'The first pass can take several minutes. Older history continues over later syncs and can take hours. ' +
      'Records become available as each sync step finishes. You can close this window; syncing continues.'
    );
  if (jobState === 'FAILED' || jobState === 'COMPLETED_WITH_ERRORS')
    return 'The connection remains available while you review the sync error.';
  return 'The read-only local mirror is ready.';
});
const oauthNotice = computed(() => {
  if (props.oauthResult === 'connected')
    return { tone: 'success', icon: 'bi bi-check-circle', text: props.oauthMessage || 'Authorization completed.' };
  if (props.oauthResult === 'denied')
    return {
      tone: 'warning',
      icon: 'bi bi-info-circle',
      text: `${props.oauthMessage || 'Authorization was denied.'} If you did not decline access, check Audience → Test users in Google Cloud and use the listed account.`,
    };
  if (props.oauthResult === 'expired')
    return { tone: 'warning', icon: 'bi bi-clock', text: props.oauthMessage || 'Authorization expired. Start again.' };
  if (props.oauthResult === 'error')
    return { tone: 'error', icon: 'bi bi-exclamation-circle', text: props.oauthMessage || 'Authorization failed.' };
  return null;
});

watch(progress, (value) => storage && saveGoogleHealthSetupProgress(storage, value), { deep: true });
watch(
  progressRedirectKey,
  (value) => {
    progress.value = reconcileGoogleHealthSetupRedirect(progress.value, value);
  },
  { immediate: true }
);
watch(
  () => [props.visible, props.oauthResult, connected.value] as const,
  ([visible, result, isConnected]) => {
    if (visible && (result || isConnected)) setActiveStep(CONNECTION_STEP);
    if (result === 'connected' || result === 'denied' || result === 'expired') manualRedirectUrl.value = '';
  },
  { immediate: true }
);
onBeforeUnmount(clearCopyFeedback);

function setupScreenshot(filename: string, alt: string, caption: string): SetupScreenshot | null {
  const src = Object.entries(screenshotModules).find(([path]) => path.endsWith(`/${filename}`))?.[1];
  return src ? { src, alt, caption } : null;
}

function openScreenshot(event: MouseEvent, screenshot: SetupScreenshot) {
  screenshotTrigger = event.currentTarget instanceof HTMLElement ? event.currentTarget : null;
  expandedScreenshot.value = screenshot;
  void nextTick(() => screenshotCloseRef.value?.focus());
}

function closeScreenshot() {
  expandedScreenshot.value = null;
  const trigger = screenshotTrigger;
  screenshotTrigger = null;
  void nextTick(() => trigger?.focus());
}

function handleScreenshotPreviewKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault();
    closeScreenshot();
    return;
  }
  if (event.key === 'Tab') {
    event.preventDefault();
    screenshotCloseRef.value?.focus();
  }
}

function setActiveStep(step: number) {
  progress.value.activeStep = Math.max(0, Math.min(steps.length - 1, step));
  void nextTick(() => setupContentRef.value?.scrollIntoView?.({ block: 'start' }));
}
function nextStep() {
  if (activeStep.value === TEST_USER_STEP && !stepComplete(TEST_USER_STEP)) return;
  if (activeStep.value > 0 && activeStep.value < SERVER_CONFIGURATION_STEP && !stepComplete(activeStep.value)) {
    progress.value.completedSteps.push(activeStep.value);
    progress.value.completedSteps.sort((left, right) => left - right);
  }
  setActiveStep(activeStep.value + 1);
}
function stepComplete(step: number): boolean {
  if (step === INTRODUCTION_STEP) return false;
  if (step === SERVER_CONFIGURATION_STEP) return serverConfigured.value;
  if (step === CONNECTION_STEP) return connected.value;
  return progress.value.completedSteps.includes(step);
}
function toggleCurrentStep(event: Event) {
  const completed = new Set(progress.value.completedSteps);
  if ((event.target as HTMLInputElement).checked) completed.add(activeStep.value);
  else completed.delete(activeStep.value);
  progress.value.completedSteps = [...completed].sort((left, right) => left - right);
}
async function copyValue(value: string, key: string) {
  await writeTextToClipboard(value);
  clearCopyFeedback();
  copiedValue.value = key;
  copyFeedbackTimer = setTimeout(() => {
    copiedValue.value = '';
    copyFeedbackTimer = null;
  }, COPY_FEEDBACK_MS);
}
function clearCopyFeedback() {
  if (copyFeedbackTimer) clearTimeout(copyFeedbackTimer);
  copyFeedbackTimer = null;
}
function configurationIssueLabel(issue: string): string {
  const labels: Record<string, string> = {
    CLIENT_ID_MISSING: 'OAuth client ID is missing.',
    CLIENT_SECRET_MISSING: 'OAuth client secret is missing.',
    REDIRECT_URI_INVALID: 'OAuth redirect URI is not accepted by the server.',
  };
  return labels[issue] ?? issue.replaceAll('_', ' ').toLowerCase();
}
function submitManualCompletion() {
  if (manualRedirectUrl.value && !manualRedirectError.value) emit('complete', manualRedirectUrl.value.trim());
}
function formatDate(value: Date): string {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'long' }).format(value);
}
function browserStorage(): Storage | null {
  try {
    return typeof window === 'undefined' ? null : window.localStorage;
  } catch {
    return null;
  }
}
</script>

<style scoped>
:global(.google-health-setup-sheet) {
  --bs-handle-pt: 0.35rem;
  --bs-handle-pb: 0;
  --bs-header-pt: 1rem;
  --bs-header-px: 1.5rem;
  --bs-header-pb: 0.9rem;
  --bs-body-pb: 0.35rem;
  background: var(--surface-sheet-solid);
}

:global(.google-health-setup-sheet .sheet-handle) {
  opacity: 0;
}

:global(.google-health-setup-sheet .sheet-title) {
  font-size: var(--text-xl-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-xl-lh);
}

:global(.google-health-setup-sheet .sheet-footer) {
  border-top: 1px solid var(--border-default);
  padding: 0.75rem 1.5rem 1rem;
}

.gh-setup-layout {
  display: grid;
  grid-template-columns: 13.5rem minmax(0, 1fr);
  min-height: 30rem;
  gap: 1.75rem;
  width: 100%;
  box-sizing: border-box;
  padding: 0.35rem 1.5rem 0.75rem;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.gh-setup-steps,
.gh-setup-section,
.gh-setup-task-list,
.gh-setup-server-tasks,
.gh-setup-scope-group {
  display: flex;
  flex-direction: column;
}

.gh-setup-steps {
  position: relative;
  gap: 0.2rem;
  padding-right: 1.25rem;
  border-right: 1px solid var(--border-default);
}

.gh-setup-progress {
  margin-bottom: 0.75rem;
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.gh-setup-step {
  position: relative;
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 2.75rem;
  padding: 0.45rem 0.55rem;
  border: 0;
  border-radius: 0.5rem;
  color: var(--text-secondary);
  background: transparent;
  font: inherit;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  text-align: left;
  gap: 0.65rem;
  cursor: pointer;
}

.gh-setup-step:not(:last-child)::after {
  position: absolute;
  top: calc(50% + 0.9rem);
  bottom: calc(-50% + 0.9rem);
  left: 1.4rem;
  width: 1px;
  background: var(--border-strong);
  content: '';
  pointer-events: none;
}

.gh-setup-step:hover,
.gh-setup-step--active {
  color: var(--text-primary);
  background: var(--accent-bg);
}

.gh-setup-step:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 1px;
}

.gh-setup-step__number {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.75rem;
  height: 1.75rem;
  flex: 0 0 auto;
  border: 1px solid var(--border-strong);
  border-radius: 50%;
  background: var(--surface-sheet-solid);
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  font-weight: var(--font-medium);
  line-height: var(--text-xs-lh);
}

.gh-setup-step__number--active {
  border-color: var(--accent);
  background: var(--accent);
  color: var(--accent-contrast);
}

.gh-setup-step__number--done {
  color: var(--success);
  border-color: color-mix(in srgb, var(--success) 42%, transparent);
  background: var(--success-bg);
}

.gh-setup-step__number--done.gh-setup-step__number--active {
  color: var(--accent-contrast);
  border-color: var(--accent);
  background: var(--accent);
}

.gh-setup-step__number .bi {
  font-size: var(--text-sm-size);
}

.gh-setup-step__label {
  min-width: 0;
}

.gh-setup-content {
  min-width: 0;
}

.gh-setup-heading {
  margin-bottom: 0.9rem;
}

.gh-setup-heading > span,
.gh-setup-heading p,
.gh-setup-note,
.gh-setup-manual-paste span,
figcaption {
  color: var(--text-muted);
}

.gh-setup-heading > span {
  font-size: var(--text-xs-size);
  font-weight: var(--font-medium);
  line-height: var(--text-xs-lh);
}

.gh-setup-heading h3 {
  margin: 0.1rem 0 0.15rem;
  color: var(--text-primary);
  font-size: var(--text-xl-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-xl-lh);
}

.gh-setup-heading p {
  margin: 0;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.gh-setup-content > .admin-message {
  margin-bottom: 0.75rem;
}

.gh-setup-section {
  width: 100%;
  align-items: flex-start;
  gap: 0.75rem;
}

.gh-setup-section p,
.gh-setup-section ol,
.gh-setup-section ul {
  margin-block: 0;
}

.gh-setup-section h4,
.gh-setup-panel h4,
.gh-setup-server-task h4,
.gh-setup-checks h4,
.gh-setup-connected h4 {
  margin: 0;
  color: var(--text-primary);
  font-size: var(--text-base-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-base-lh);
}

.gh-setup-privacy {
  display: grid;
  grid-template-columns: 4.5rem minmax(0, 1fr);
  align-items: start;
  width: 100%;
  box-sizing: border-box;
  gap: 1rem;
  padding: 1.15rem 1.3rem;
  border: 1px solid var(--border-default);
  border-radius: 0.7rem;
  background: color-mix(in srgb, var(--success-bg) 36%, transparent);
}

.gh-setup-privacy__icon {
  display: inline-flex;
  width: 4.5rem;
  height: 4.5rem;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--success-bg);
  color: var(--success);
}

.gh-setup-privacy__icon .bi {
  font-size: var(--text-2xl-size);
}

.gh-setup-privacy__body {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.25rem;
}

.gh-setup-privacy__body > strong {
  color: var(--text-primary);
  font-size: var(--text-base-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-base-lh);
}

.gh-setup-privacy__body p {
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.gh-setup-privacy__body small {
  margin-top: 0.3rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-setup-privacy__path {
  display: inline-flex;
  align-items: center;
  gap: 0.55rem;
  margin-top: 0.35rem;
  padding: 0.2rem 0.65rem;
  border: 1px solid color-mix(in srgb, var(--success) 20%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--success-bg) 62%, transparent);
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-xs-lh);
}

.gh-setup-privacy__path .bi {
  font-size: var(--text-xs-size);
}

.gh-setup-overview-grid,
.gh-setup-split,
.gh-setup-connect-grid {
  display: grid;
  width: 100%;
  gap: 0.9rem;
}

.gh-setup-overview-grid,
.gh-setup-split {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.gh-setup-panel {
  min-width: 0;
  box-sizing: border-box;
  padding: 1rem 1.1rem;
  border: 1px solid var(--border-default);
  border-radius: 0.7rem;
  background: color-mix(in srgb, var(--surface-elevated) 40%, transparent);
}

.gh-setup-icon-list,
.gh-setup-timeline {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  margin-top: 0.8rem !important;
  padding: 0;
  list-style: none;
}

.gh-setup-icon-list li,
.gh-setup-timeline li {
  display: flex;
  align-items: center;
  gap: 0.7rem;
}

.gh-setup-icon-list i {
  display: inline-flex;
  width: 2.25rem;
  height: 2.25rem;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 0.45rem;
  background: var(--surface-hover);
  color: var(--text-muted);
  font-size: var(--text-base-size);
}

.gh-setup-timeline li {
  position: relative;
  min-height: 1.9rem;
}

.gh-setup-timeline li:not(:last-child)::after {
  position: absolute;
  top: 1.65rem;
  bottom: -0.7rem;
  left: 0.8rem;
  width: 1px;
  background: var(--border-default);
  content: '';
}

.gh-setup-timeline li > span,
.gh-setup-numbered-list li > span {
  display: inline-flex;
  width: 1.65rem;
  height: 1.65rem;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--accent-bg);
  color: var(--accent-text);
  font-size: var(--text-xs-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-xs-lh);
}

.gh-setup-testing-note {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  margin-top: 0.8rem !important;
  padding-top: 0.75rem;
  border-top: 1px solid var(--border-subtle);
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-setup-testing-note .bi {
  flex: 0 0 auto;
  color: var(--warning);
  font-size: var(--text-sm-size);
}

.gh-setup-instruction-grid {
  display: grid;
  width: 100%;
  gap: 1rem;
}

.gh-setup-instruction-grid--with-image {
  grid-template-columns: minmax(17rem, 1fr) minmax(14rem, 18rem);
  align-items: start;
}

.gh-setup-task-list,
.gh-setup-server-tasks {
  gap: 0.65rem;
}

.gh-setup-task-list--compact {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  width: 100%;
}

.gh-setup-task-card {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 0.75rem;
  box-sizing: border-box;
  padding: 0.85rem;
  border: 1px solid var(--border-default);
  border-radius: 0.65rem;
  background: color-mix(in srgb, var(--surface-elevated) 40%, transparent);
}

.gh-setup-task-card__number {
  display: inline-flex;
  width: 1.8rem;
  height: 1.8rem;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--accent);
  color: var(--accent-contrast);
  font-size: var(--text-xs-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-xs-lh);
}

.gh-setup-task-card h4 {
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.gh-setup-task-card p {
  margin-top: 0.18rem !important;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-setup-inline-note {
  display: flex;
  width: 100%;
  box-sizing: border-box;
  align-items: flex-start;
  gap: 0.65rem;
  padding: 0.7rem 0.85rem;
  border: 1px solid var(--border-default);
  border-radius: 0.6rem;
  background: var(--surface-elevated);
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-setup-inline-note > .bi {
  flex: 0 0 auto;
  color: var(--accent-text);
  font-size: var(--text-sm-size);
}

.gh-setup-inline-note--warning {
  border-color: color-mix(in srgb, var(--warning) 22%, transparent);
  background: var(--warning-bg);
}

.gh-setup-inline-note--warning > .bi {
  color: var(--warning);
}

.gh-setup-credentials-note {
  border-color: color-mix(in srgb, var(--warning) 55%, transparent);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.gh-setup-credentials-note__title {
  display: block;
  margin-bottom: 0.25rem;
  color: var(--warning);
}

.gh-setup-external-action {
  display: inline-flex;
  width: fit-content;
  min-height: 2.75rem;
  box-sizing: border-box;
  align-items: center;
  justify-content: center;
  gap: 0.45rem;
  padding: 0.5rem 0.8rem;
  border-radius: 0.5rem;
  background: var(--accent);
  color: var(--accent-contrast);
  font-size: var(--text-sm-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-sm-lh);
  text-decoration: none;
}

.gh-setup-external-action:hover {
  background: var(--accent-hover);
}

.gh-setup-external-action:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.gh-setup-external-action .bi {
  font-size: var(--text-sm-size);
}

.gh-setup-numbered-list {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
  margin-top: 0.75rem !important;
  padding: 0;
  list-style: none;
}

.gh-setup-numbered-list li {
  display: flex;
  align-items: flex-start;
  gap: 0.65rem;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.gh-setup-numbered-list li > div {
  min-width: 0;
}

.gh-setup-panel--instructions,
.gh-setup-panel--scopes,
.gh-setup-connect-instructions,
.gh-setup-connect-form {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.75rem;
}

.gh-setup-panel--instructions .gh-setup-numbered-list {
  flex: 1 1 auto;
}

.gh-setup-copy-block {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 0.55rem;
}

.gh-setup-copy-block code,
.gh-setup-copy-block pre,
.gh-setup-scope-group code {
  min-width: 0;
  margin: 0;
  padding: 0.55rem 0.65rem;
  border: 1px solid var(--code-border);
  border-radius: 0.5rem;
  background: var(--code-bg);
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
  user-select: text;
}

.gh-setup-copy-block code,
.gh-setup-copy-block pre {
  flex: 1;
}

.gh-setup-copy-block--environment {
  align-items: flex-start;
}

.gh-setup-scope-group {
  width: 100%;
  gap: 0.35rem;
}

.gh-setup-scope-group > strong {
  color: var(--text-primary);
  font-size: var(--text-xs-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-xs-lh);
}

.gh-setup-panel--scopes {
  gap: 0.75rem;
}

.gh-setup-panel-action {
  align-self: stretch;
  justify-content: center;
}

.gh-setup-screenshot {
  width: 100%;
  max-width: 18rem;
  min-width: 0;
  margin: 0;
}

.gh-setup-screenshot__button {
  position: relative;
  display: block;
  width: 100%;
  min-height: 2.75rem;
  overflow: hidden;
  padding: 0;
  border: 1px solid var(--border-default);
  border-radius: 0.65rem;
  background: var(--code-bg);
  cursor: zoom-in;
}

.gh-setup-screenshot__button:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.gh-setup-screenshot__button img {
  display: block;
  width: 100%;
  height: 10.5rem;
  object-fit: contain;
}

.gh-setup-screenshot__action {
  position: absolute;
  right: 0.5rem;
  bottom: 0.5rem;
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.3rem 0.55rem;
  border-radius: 999px;
  background: color-mix(in srgb, var(--surface-sheet-solid) 90%, transparent);
  box-shadow: var(--shadow-sm);
  color: var(--text-primary);
  font-size: var(--text-xs-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-xs-lh);
}

.gh-setup-screenshot figcaption {
  margin-top: 0.4rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-setup-consent-reference {
  display: grid;
  width: 100%;
  box-sizing: border-box;
  grid-template-columns: minmax(0, 1fr) minmax(14rem, 18rem);
  align-items: center;
  gap: 1rem;
  padding: 0.9rem 1rem;
  border: 1px solid var(--border-default);
  border-radius: 0.7rem;
  background: color-mix(in srgb, var(--surface-elevated) 45%, transparent);
}

.gh-setup-consent-reference__copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 0.35rem;
}

.gh-setup-consent-reference__copy p {
  color: var(--text-secondary);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-setup-screenshot-preview {
  position: fixed;
  z-index: var(--z-modal-mask);
  inset: 0;
  display: grid;
  box-sizing: border-box;
  place-items: center;
  padding: 1rem;
  background: var(--backdrop);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.gh-setup-screenshot-preview__panel {
  display: flex;
  width: min(74rem, 96vw);
  max-height: calc(100dvh - 2rem);
  overflow: hidden;
  flex-direction: column;
  border: 1px solid var(--border-medium);
  border-radius: 0.8rem;
  background: var(--surface-sheet-solid);
  box-shadow: var(--shadow-lg);
}

.gh-setup-screenshot-preview__header {
  display: flex;
  flex: 0 0 auto;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.8rem 0.9rem;
  border-bottom: 1px solid var(--border-default);
}

.gh-setup-screenshot-preview__header h3,
.gh-setup-screenshot-preview__header p {
  margin: 0;
}

.gh-setup-screenshot-preview__header h3 {
  color: var(--text-primary);
  font-size: var(--text-base-size);
  line-height: var(--text-base-lh);
}

.gh-setup-screenshot-preview__header p {
  margin-top: 0.15rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-setup-screenshot-preview__close {
  display: inline-flex;
  width: 2.75rem;
  height: 2.75rem;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid var(--border-medium);
  border-radius: 50%;
  background: var(--surface-hover);
  color: var(--text-secondary);
  cursor: pointer;
}

.gh-setup-screenshot-preview__close:hover {
  background: var(--surface-active);
  color: var(--text-primary);
}

.gh-setup-screenshot-preview__close:focus-visible {
  outline: 2px solid var(--accent);
  outline-offset: 2px;
}

.gh-setup-screenshot-preview__panel > img {
  display: block;
  width: 100%;
  min-height: 0;
  flex: 1 1 auto;
  object-fit: contain;
}

.gh-setup-alternative {
  width: 100%;
  border: 1px solid var(--border-default);
  border-radius: 0.6rem;
  background: color-mix(in srgb, var(--surface-elevated) 40%, transparent);
}

.gh-setup-alternative summary {
  padding: 0.65rem 0.75rem;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  font-weight: var(--font-semibold);
  line-height: var(--text-sm-lh);
  cursor: pointer;
}

.gh-setup-alternative > div {
  padding: 0 0.75rem 0.75rem;
}

.gh-setup-alternative p {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-setup-alternative p + p {
  margin-top: 0.6rem !important;
}

.gh-setup-credential-panel {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.55rem;
}

.gh-setup-credential-panel__icon {
  display: inline-flex;
  width: 2.75rem;
  height: 2.75rem;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--accent-bg);
  color: var(--accent-text);
}

.gh-setup-credential-panel__icon .bi {
  font-size: var(--text-lg-size);
}

.gh-setup-credential-panel > div {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.gh-setup-credential-panel > div .bi {
  color: var(--success);
  font-size: var(--text-sm-size);
}

.gh-setup-credential-panel p {
  margin-top: auto !important;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-setup-server-grid {
  display: grid;
  width: 100%;
  grid-template-columns: minmax(0, 1fr) 14rem;
  gap: 0.9rem;
}

.gh-setup-server-tasks {
  overflow: hidden;
  gap: 0;
  border: 1px solid var(--border-default);
  border-radius: 0.7rem;
}

.gh-setup-server-task {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.85rem;
  background: color-mix(in srgb, var(--surface-elevated) 40%, transparent);
}

.gh-setup-server-task + .gh-setup-server-task {
  border-top: 1px solid var(--border-default);
}

.gh-setup-server-task__body {
  display: flex;
  min-width: 0;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 0.45rem;
}

.gh-setup-server-task p {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-setup-file-location {
  display: inline-flex;
  width: fit-content;
  align-items: center;
  gap: 0.4rem;
  padding: 0.3rem 0.55rem;
  border-radius: 0.4rem;
  background: var(--code-bg);
  color: var(--text-secondary);
  font-family: monospace;
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-setup-file-location .bi {
  font-size: var(--text-sm-size);
}

.gh-setup-checks {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: stretch;
  gap: 0.65rem;
  box-sizing: border-box;
  padding: 1rem;
  border: 1px solid var(--border-default);
  border-radius: 0.7rem;
  background: color-mix(in srgb, var(--surface-elevated) 50%, transparent);
}

.gh-setup-checks > p {
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-setup-check-row {
  display: flex;
  align-items: flex-start;
  gap: 0.5rem;
  padding: 0.55rem;
  border: 1px solid color-mix(in srgb, var(--warning) 28%, transparent);
  border-radius: 0.5rem;
  background: color-mix(in srgb, var(--warning-bg) 60%, transparent);
  color: var(--warning-text);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-setup-check-row .bi {
  flex: 0 0 auto;
  color: var(--warning);
  font-size: var(--text-sm-size);
}

.gh-setup-check-row--success {
  border-color: color-mix(in srgb, var(--success) 24%, transparent);
  background: var(--success-bg);
  color: var(--success);
}

.gh-setup-check-row--success .bi {
  color: var(--success);
}

.gh-setup-checks__action {
  width: 100%;
  justify-content: center;
}

.gh-setup-connected {
  display: flex;
  width: 100%;
  box-sizing: border-box;
  align-items: center;
  gap: 0.9rem;
  padding: 1rem 1.1rem;
  border: 1px solid color-mix(in srgb, var(--success) 24%, transparent);
  border-radius: 0.7rem;
  background: var(--success-bg);
}

.gh-setup-connected__icon {
  display: inline-flex;
  width: 3rem;
  height: 3rem;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: color-mix(in srgb, var(--success-bg) 75%, var(--surface-sheet-solid));
  color: var(--success);
}

.gh-setup-connected__icon .bi {
  font-size: var(--text-xl-size);
}

.gh-setup-connected p {
  color: var(--text-muted);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.gh-setup-grants > div {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
  margin-top: 0.6rem;
}

.gh-setup-connect-grid {
  grid-template-columns: minmax(0, 1.15fr) minmax(17rem, 0.85fr);
}

.gh-setup-connect-form {
  align-self: stretch;
}

.gh-setup-manual-paste {
  display: flex;
  flex-direction: column;
  width: 100%;
  gap: 0.25rem;
  color: var(--text-primary);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
}

.gh-setup-manual-paste > span {
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-setup-manual-paste textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 0.65rem;
  border: 1px solid var(--border-medium);
  border-radius: 0.5rem;
  color: var(--text-primary);
  background: var(--surface-sheet-solid);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
  resize: vertical;
}

.gh-setup-manual-paste textarea:focus {
  border-color: var(--accent);
  outline: 2px solid var(--accent-subtle);
}

.gh-setup-validation-error {
  color: var(--error);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-setup-secure-code-note {
  display: flex;
  align-items: flex-start;
  gap: 0.45rem;
  color: var(--text-muted);
  font-size: var(--text-xs-size);
  line-height: var(--text-xs-lh);
}

.gh-setup-secure-code-note .bi {
  flex: 0 0 auto;
  color: var(--success);
  font-size: var(--text-sm-size);
}

.gh-setup-proxy-connect {
  display: flex;
  max-width: 28rem;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.55rem;
}

.gh-setup-proxy-connect > span {
  display: inline-flex;
  width: 2.75rem;
  height: 2.75rem;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--accent-bg);
  color: var(--accent-text);
}

.gh-setup-proxy-connect > span .bi {
  font-size: var(--text-lg-size);
}

.gh-setup-complete {
  display: flex;
  align-items: center;
  width: fit-content;
  min-height: 2.75rem;
  box-sizing: border-box;
  margin-top: 0.9rem;
  gap: 0.5rem;
  padding: 0.45rem 0.65rem;
  border: 1px solid var(--border-default);
  border-radius: 0.5rem;
  background: color-mix(in srgb, var(--surface-elevated) 40%, transparent);
  color: var(--text-secondary);
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  cursor: pointer;
}

.gh-setup-final-actions,
.gh-setup-footer {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.gh-setup-final-actions {
  margin-top: 0.15rem;
}

.gh-setup-footer {
  justify-content: space-between;
  width: 100%;
}

@media (max-width: 780px) {
  :global(.google-health-setup-sheet) {
    --bs-header-px: 1rem;
  }

  :global(.google-health-setup-sheet .sheet-title) {
    font-size: var(--text-lg-size);
    line-height: var(--text-lg-lh);
  }

  :global(.google-health-setup-sheet .sheet-footer) {
    padding: 0.65rem 1rem max(0.75rem, env(safe-area-inset-bottom));
  }

  .gh-setup-layout {
    grid-template-columns: 1fr;
    min-height: 0;
    gap: 1rem;
    padding: 0.25rem 1rem 0.75rem;
  }

  .gh-setup-steps {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 0.25rem;
    padding: 0 0 0.8rem;
    border-right: 0;
    border-bottom: 1px solid var(--border-default);
  }

  .gh-setup-progress {
    grid-column: 1 / -1;
    margin-bottom: 0.25rem;
  }

  .gh-setup-step {
    min-height: 2.75rem;
    padding: 0.35rem;
    gap: 0.4rem;
  }

  :global(.google-health-setup-sheet .sheet-scroll-hint__button),
  .gh-setup-copy-block :deep(.p-button),
  :deep(.gh-setup-panel-action.p-button) {
    min-height: 2.75rem;
  }

  :global(.google-health-setup-sheet .sheet-scroll-hint__button) {
    min-width: 2.75rem;
  }

  .gh-setup-step:not(:last-child)::after {
    display: none;
  }

  .gh-setup-step__number {
    width: 1.5rem;
    height: 1.5rem;
  }

  .gh-setup-step__label {
    font-size: var(--text-xs-size);
    line-height: var(--text-xs-lh);
    overflow-wrap: anywhere;
    white-space: normal;
  }

  .gh-setup-privacy,
  .gh-setup-overview-grid,
  .gh-setup-split,
  .gh-setup-instruction-grid--with-image,
  .gh-setup-task-list--compact,
  .gh-setup-server-grid,
  .gh-setup-connect-grid,
  .gh-setup-consent-reference {
    grid-template-columns: 1fr;
  }

  .gh-setup-screenshot-preview {
    padding: 0.5rem;
  }

  .gh-setup-screenshot-preview__panel {
    width: 100%;
    max-height: calc(100dvh - 1rem);
  }

  .gh-setup-privacy {
    grid-template-columns: 3rem minmax(0, 1fr);
    padding: 0.9rem;
  }

  .gh-setup-privacy__icon {
    width: 3rem;
    height: 3rem;
  }

  .gh-setup-copy-block {
    align-items: stretch;
    flex-direction: column;
  }
}

@media (max-width: 520px) {
  .gh-setup-steps {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .gh-setup-privacy {
    display: flex;
    flex-direction: column;
  }
}
</style>
