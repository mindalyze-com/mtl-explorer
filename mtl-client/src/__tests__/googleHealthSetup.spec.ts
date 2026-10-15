import { describe, expect, it, vi } from 'vitest';
import {
  assessGoogleHealthRedirectUri,
  buildGoogleHealthRedirectUri,
  validateGoogleHealthManualRedirectUrl,
} from '@/features/health/utils/googleHealthSetup';
import {
  loadGoogleHealthSetupProgress,
  reconcileGoogleHealthSetupRedirect,
  saveGoogleHealthSetupProgress,
} from '@/features/health/utils/googleHealthSetupProgress';

describe('Google Health setup helpers', () => {
  it('removes obsolete progress only after the current version is saved', () => {
    const storage = window.localStorage;
    storage.clear();
    storage.setItem('mtl.google-health-setup.v2', '{}');
    storage.setItem('mtl.google-health-setup.v3', '{}');
    const progress = loadGoogleHealthSetupProgress(storage);
    saveGoogleHealthSetupProgress(storage, progress);
    expect(storage.getItem('mtl.google-health-setup.v4')).toBe(JSON.stringify(progress));
    expect(storage.getItem('mtl.google-health-setup.v3')).toBeNull();
    expect(storage.getItem('mtl.google-health-setup.v2')).toBeNull();
  });

  it('keeps old progress if saving the current version fails', () => {
    const removeItem = vi.fn();
    const storage = {
      setItem: () => {
        throw new Error('Storage is full');
      },
      removeItem,
    } as unknown as Storage;
    expect(() =>
      saveGoogleHealthSetupProgress(storage, {
        activeStep: 3,
        completedSteps: [1, 2],
        lastProposedRedirectUri: '',
      })
    ).toThrow('Storage is full');
    expect(removeItem).not.toHaveBeenCalled();
  });

  it('builds the callback from the browser-visible API base', () => {
    expect(buildGoogleHealthRedirectUri('/mtl/', 'http://localhost:18080', '/api/google-health/oauth/callback')).toBe(
      'http://localhost:18080/mtl/api/google-health/oauth/callback'
    );
    expect(
      buildGoogleHealthRedirectUri('/proxy/mtl/', 'https://trails.example.org', '/api/google-health/oauth/callback')
    ).toBe('https://trails.example.org/proxy/mtl/api/google-health/oauth/callback');
    expect(
      buildGoogleHealthRedirectUri(
        'https://api.example.org/custom/',
        'https://app.example.org',
        '/api/google-health/oauth/callback'
      )
    ).toBe('https://api.example.org/custom/api/google-health/oauth/callback');
  });

  it('explains invalid HTTP and raw IP redirects without rejecting localhost', () => {
    expect(assessGoogleHealthRedirectUri('http://localhost:18080/mtl/api/google-health/oauth/callback')).toEqual([
      expect.objectContaining({ tone: 'info' }),
    ]);
    expect(assessGoogleHealthRedirectUri('http://trails.example.org/mtl/api/google-health/oauth/callback')).toEqual([
      expect.objectContaining({ tone: 'error', text: expect.stringContaining('HTTPS') }),
    ]);
    expect(assessGoogleHealthRedirectUri('https://192.168.1.20/mtl/api/google-health/oauth/callback')).toEqual([
      expect.objectContaining({ tone: 'error', text: expect.stringContaining('raw LAN') }),
    ]);
  });

  it('validates the complete manual OAuth return address before submission', () => {
    const expected = 'http://127.0.0.1:1/mtl-explorer-oauth';

    expect(validateGoogleHealthManualRedirectUrl(`${expected}?code=code&state=state`, expected)).toBe('');
    expect(validateGoogleHealthManualRedirectUrl(`${expected}?error=access_denied&state=state`, expected)).toBe('');
    expect(validateGoogleHealthManualRedirectUrl('not a URL', expected)).toContain('not a valid URL');
    expect(validateGoogleHealthManualRedirectUrl('http://localhost:1/?code=code&state=state', expected)).toContain(
      expected
    );
    expect(validateGoogleHealthManualRedirectUrl(`${expected}?code=code`, expected)).toContain('authorization state');
    expect(validateGoogleHealthManualRedirectUrl(`${expected}?state=state`, expected)).toContain(
      'no valid Google authorization result'
    );
    expect(validateGoogleHealthManualRedirectUrl(`${expected}?code=one&code=two&state=state`, expected)).toContain(
      'duplicate parameters'
    );
  });

  it('persists only progress metadata and reopens the URI step after the public address changes', () => {
    const storage = window.localStorage;
    storage.clear();
    saveGoogleHealthSetupProgress(storage, {
      activeStep: 5,
      completedSteps: [1, 2, 3, 4],
      lastProposedRedirectUri: 'https://old.example/mtl/api/google-health/oauth/callback',
    });

    const loaded = loadGoogleHealthSetupProgress(storage);
    const reconciled = reconcileGoogleHealthSetupRedirect(
      loaded,
      'https://new.example/mtl/api/google-health/oauth/callback'
    );

    expect(reconciled).toEqual({
      activeStep: 5,
      completedSteps: [1, 2, 3],
      lastProposedRedirectUri: 'https://new.example/mtl/api/google-health/oauth/callback',
    });
    expect(storage.getItem('mtl.google-health-setup.v4')).not.toContain('secret');
  });

  it('reopens v3 progress at the required test-user step', () => {
    const storage = window.localStorage;
    storage.clear();
    storage.setItem(
      'mtl.google-health-setup.v3',
      JSON.stringify({
        activeStep: 6,
        completedSteps: [1, 2, 3, 4],
        lastProposedRedirectUri: 'desktop-app-manual-v3',
      })
    );

    expect(loadGoogleHealthSetupProgress(storage)).toEqual({
      activeStep: 3,
      completedSteps: [1, 2, 4, 5],
      lastProposedRedirectUri: 'desktop-app-manual-v3',
    });
  });

  it('migrates v2 progress and requires the new test-user confirmation', () => {
    const storage = window.localStorage;
    storage.clear();
    storage.setItem(
      'mtl.google-health-setup.v2',
      JSON.stringify({
        activeStep: 7,
        completedSteps: [0, 1, 3, 4, 5, 6, 7],
        lastProposedRedirectUri: 'https://trails.example/mtl/api/google-health/oauth/callback',
      })
    );

    expect(loadGoogleHealthSetupProgress(storage)).toEqual({
      activeStep: 3,
      completedSteps: [0, 1, 2, 4, 5, 6, 7],
      lastProposedRedirectUri: 'https://trails.example/mtl/api/google-health/oauth/callback',
    });
  });
});
