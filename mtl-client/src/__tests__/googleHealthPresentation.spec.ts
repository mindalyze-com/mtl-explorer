import { describe, expect, it } from 'vitest';
import {
  googleHealthReauthorizationExplanation,
  googleHealthStatusPresentation,
} from '@/features/health/utils/googleHealthPresentation';

describe('Google Health status presentation', () => {
  it.each([
    { configurationState: 'CONFIGURED', connectionState: 'CONNECTED', authorizationAction: 'NONE' },
    { configurationState: 'CONFIGURED', connectionState: 'CONNECTED', authorizationAction: 'EXPIRING_SOON' },
    { configurationState: 'CONFIGURED', connectionState: 'DISCONNECTED', authorizationAction: 'REAUTHORIZE' },
    { configurationState: 'MISSING_CONFIGURATION', connectionState: 'RECONNECT_REQUIRED' },
    { configurationState: 'DISABLED', connectionState: 'RECONNECT_REQUIRED' },
  ] as const)(
    'limits the renewal explanation to configured connections needing reauthorization: $connectionState / $authorizationAction',
    (status) => {
      expect(googleHealthReauthorizationExplanation(status)).toBeNull();
    }
  );

  it('explains reported expiry and distinguishes it from an unknown renewal failure', () => {
    const connection = { configurationState: 'CONFIGURED' as const, connectionState: 'RECONNECT_REQUIRED' as const };
    const expired = googleHealthReauthorizationExplanation({
      ...connection,
      refreshTokenExpiresAt: new Date('2000-01-01T00:00:00Z'),
    });
    expect(expired?.heading).toBe('Google Health authorization has expired.');
    expect(expired?.detail).toContain('Google limits access to seven days');
    expect(expired?.detail).toContain('Your Google Cloud and server setup can stay in place');
    const unknown = googleHealthReauthorizationExplanation(connection);
    expect(unknown?.heading).toBe('Google Health needs reauthorization.');
    expect(unknown?.detail).toContain('may have expired or been revoked');
    expect(unknown?.detail).toContain('Stored records and routes are kept');
    const unreadable = googleHealthReauthorizationExplanation({
      ...connection,
      lastError: 'Google Health must be reconnected because the saved authorization cannot be read',
    });
    expect(unreadable?.detail).toContain('could not read the saved Google authorization');
    expect(unreadable?.detail).not.toContain('Testing mode');
  });

  it('labels generated data without claiming an account connection', () => {
    expect(
      googleHealthStatusPresentation(
        {
          configurationState: 'DISABLED',
          connectionState: 'DISCONNECTED',
        },
        { syntheticData: true }
      ).label
    ).toBe('Synthetic demo data');
  });

  it('distinguishes a finished pass from pending history on every status pill', () => {
    const status = {
      configurationState: 'CONFIGURED' as const,
      connectionState: 'CONNECTED' as const,
      job: { state: 'COMPLETED' as const },
      syncStates: [{ backfillComplete: false }],
    };
    expect(googleHealthStatusPresentation(status).label).toBe('History pending');
    expect(googleHealthStatusPresentation(status, { connectionOnly: true }).label).toBe('Connected');
    expect(googleHealthStatusPresentation({ ...status, job: { state: 'RUNNING' } }).label).toBe('Syncing');
    expect(googleHealthStatusPresentation({ ...status, job: { state: 'COMPLETED_WITH_ERRORS' } }).label).toBe(
      'Sync needs attention'
    );
    expect(googleHealthStatusPresentation({ ...status, authorizationAction: 'REAUTHORIZE' }).label).toBe(
      'Reauthorization required'
    );
    expect(googleHealthStatusPresentation({ ...status, syncStates: [{ backfillComplete: true }] }).label).toBe(
      'Connected'
    );
  });

  it('uses one status mapping for connection, authorization, and running jobs', () => {
    expect(googleHealthStatusPresentation(null, { loading: true })).toEqual(
      expect.objectContaining({ label: 'Loading', className: 'admin-status-pill--loading' })
    );
    expect(
      googleHealthStatusPresentation({ configurationState: 'MISSING_CONFIGURATION', connectionState: 'DISCONNECTED' })
    ).toEqual(expect.objectContaining({ label: 'Setup required', className: 'admin-status-pill--warning' }));
    expect(
      googleHealthStatusPresentation({
        configurationState: 'CONFIGURED',
        connectionState: 'CONNECTED',
        authorizationAction: 'EXPIRING_SOON',
      })
    ).toEqual(expect.objectContaining({ label: 'Reauthorize soon', className: 'admin-status-pill--warning' }));
    expect(
      googleHealthStatusPresentation(
        { configurationState: 'CONFIGURED', connectionState: 'CONNECTED' },
        { syncing: true }
      )
    ).toEqual(expect.objectContaining({ label: 'Syncing', className: 'admin-status-pill--loading' }));
    expect(googleHealthStatusPresentation({ configurationState: 'CONFIGURED', connectionState: 'CONNECTED' })).toEqual(
      expect.objectContaining({ label: 'Connected', className: 'admin-status-pill--success' })
    );
  });
});
