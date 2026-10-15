import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { createUuid, randomText } from '@/utils/random';

// Isolate the randomness under test from the diagnostic ID generated for its warning.
vi.mock('@/utils/clientDiagnosticStore', () => ({
  newClientEventId: () => 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  clientDiagnosticStore: { capture: vi.fn().mockResolvedValue(undefined) },
}));

const UUID_V4_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const NATIVE_UUID = '6a3b3c50-dfa7-4d7b-b641-6d8d459c21ec';
const getRandomValues = vi.fn((bytes: Uint8Array) => bytes.fill(15));

beforeEach(() => {
  getRandomValues.mockClear();
  vi.spyOn(console, 'warn').mockImplementation(() => {});
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('shared random ID helpers', () => {
  it('uses native UUID creation with its receiver and no fallback warning', () => {
    const webCrypto = {
      randomUUID: vi.fn(function (this: unknown) {
        expect(this).toBe(webCrypto);
        return NATIVE_UUID;
      }),
      getRandomValues,
    };
    vi.stubGlobal('crypto', webCrypto);
    expect(createUuid()).toBe(NATIVE_UUID);
    expect(webCrypto.randomUUID).toHaveBeenCalledOnce();
    expect(getRandomValues).not.toHaveBeenCalled();
    expect(console.warn).not.toHaveBeenCalled();
  });

  it('falls back to secure RandomText and warns on each call without native UUID support', () => {
    vi.stubGlobal('crypto', { getRandomValues });
    for (let attempt = 0; attempt < 3; attempt++) expect(createUuid()).toMatch(UUID_V4_PATTERN);
    expect(getRandomValues).toHaveBeenCalledTimes(3);
    expect(console.warn).toHaveBeenCalledTimes(3);
    expect(console.warn).toHaveBeenCalledWith(
      '[random] crypto.randomUUID failed or is unavailable; using RandomText UUID.',
      expect.objectContaining({ context: expect.objectContaining({ errorType: 'TypeError' }) })
    );
  });

  it('recovers when an exposed native UUID method throws', () => {
    vi.stubGlobal('crypto', {
      randomUUID: () => {
        throw new DOMException('UUID creation blocked', 'SecurityError');
      },
      getRandomValues,
    });
    expect(createUuid()).toMatch(UUID_V4_PATTERN);
    expect(getRandomValues).toHaveBeenCalledOnce();
    expect(console.warn).toHaveBeenCalledWith(
      expect.stringContaining('crypto.randomUUID'),
      expect.objectContaining({
        context: expect.objectContaining({ errorType: 'SecurityError' }),
        detail: 'UUID creation blocked',
      })
    );
  });

  it.each([
    ['missing crypto', undefined],
    ['missing methods', {}],
    [
      'throwing methods',
      {
        randomUUID: () => {
          throw new Error('UUID blocked');
        },
        getRandomValues: () => {
          throw new Error('Random values blocked');
        },
      },
    ],
  ])('keeps IDs valid and warns for both fallback steps with %s', (_label, webCrypto) => {
    vi.stubGlobal('crypto', webCrypto);
    const first = createUuid();
    const second = createUuid();
    expect(first).toMatch(UUID_V4_PATTERN);
    expect(second).toMatch(UUID_V4_PATTERN);
    expect(first).not.toBe(second);
    expect(console.warn).toHaveBeenCalledTimes(4);
    expect(console.warn).toHaveBeenCalledWith(
      '[random] crypto.getRandomValues failed or is unavailable; using Math.random RandomText.',
      expect.objectContaining({ message: expect.any(String) })
    );
  });

  it('recovers when access to the crypto object itself throws', () => {
    vi.stubGlobal('crypto', undefined);
    Object.defineProperty(globalThis, 'crypto', {
      configurable: true,
      get: () => {
        throw new DOMException('Crypto access blocked', 'SecurityError');
      },
    });
    expect(createUuid()).toMatch(UUID_V4_PATTERN);
    expect(console.warn).toHaveBeenCalledTimes(2);
  });

  it('keeps fallback diagnostics useful while redacting sensitive error details', () => {
    vi.stubGlobal('crypto', {
      randomUUID: () => {
        throw new Error('UUID blocked token=private-value');
      },
      getRandomValues,
    });
    expect(createUuid()).toMatch(UUID_V4_PATTERN);
    expect(console.warn).toHaveBeenCalledWith(
      expect.stringContaining('crypto.randomUUID'),
      expect.objectContaining({
        context: expect.objectContaining({ errorType: 'Error' }),
        detail: 'UUID blocked token=<redacted>',
        stackTrace: expect.stringContaining('Error: UUID blocked token=<redacted>'),
      })
    );
  });

  it('returns the requested RandomText length, including odd lengths, without warnings on success', () => {
    vi.stubGlobal('crypto', { getRandomValues });
    expect(randomText()).toMatch(/^[0-9a-f]{32}$/);
    expect(randomText(5)).toBe('0f0f0');
    expect(console.warn).not.toHaveBeenCalled();
  });

  it('uses plain random text and a warning when random values fail independently', () => {
    vi.stubGlobal('crypto', {
      getRandomValues: () => {
        throw new Error('Random values blocked');
      },
    });
    vi.spyOn(Math, 'random').mockReturnValue(0.5);
    expect(randomText(5)).toBe('80808');
    expect(console.warn).toHaveBeenCalledOnce();
  });

  it('rejects invalid lengths before invoking crypto', () => {
    vi.stubGlobal('crypto', { getRandomValues });
    expect(randomText(0)).toBe('');
    for (const length of [-1, 1.5, Number.NaN, Number.POSITIVE_INFINITY]) {
      expect(() => randomText(length)).toThrow(RangeError);
    }
    expect(getRandomValues).not.toHaveBeenCalled();
    expect(console.warn).not.toHaveBeenCalled();
  });
});
