import { warnSanitized } from '@/utils/safeLogging';

const DEFAULT_RANDOM_TEXT_LENGTH = 32;
const HEX_RADIX = 16;
const HEX_DIGITS_PER_BYTE = 2;
const BYTE_VALUE_COUNT = 256;
const UUID_RANDOM_TEXT_LENGTH = 32;
const UUID_VERSION_INDEX = 12;
const UUID_VERSION_4 = '4';
const UUID_VARIANT_INDEX = 16;
const UUID_VARIANT_MASK = 0x03;
const UUID_RFC_VARIANT = 0x08;

/** Synchronous randomness only; the fallback must never replace hashing or encryption. */
function withCryptoFallback<T>(
  operation: string,
  generate: (webCrypto: Crypto) => T,
  fallbackName: string,
  fallback: () => T,
  reportFailures = true
): T {
  try {
    const webCrypto = globalThis.crypto;
    if (!webCrypto) throw new Error('Web Crypto is unavailable');
    return generate(webCrypto);
  } catch (error) {
    if (reportFailures) {
      warnSanitized(`[random] ${operation} failed or is unavailable; using ${fallbackName}.`, error, {
        topic1: 'application',
        topic2: 'random',
        topic3: 'withCryptoFallback',
      });
    }
    return fallback();
  }
}

/** Hexadecimal text for object IDs. The Math.random fallback is unsuitable for secrets. */
export function randomText(length = DEFAULT_RANDOM_TEXT_LENGTH): string {
  return randomTextWithReporting(length, true);
}

function randomTextWithReporting(length: number, reportFailures: boolean): string {
  if (!Number.isSafeInteger(length) || length < 0) throw new RangeError('RandomText length must be non-negative');
  if (length === 0) return '';
  const byteCount = Math.ceil(length / HEX_DIGITS_PER_BYTE);
  const bytes = withCryptoFallback(
    'crypto.getRandomValues',
    (webCrypto) => webCrypto.getRandomValues(new Uint8Array(byteCount)),
    'Math.random RandomText',
    () => Uint8Array.from({ length: byteCount }, () => Math.floor(Math.random() * BYTE_VALUE_COUNT)),
    reportFailures
  );
  return Array.from(bytes, (byte) => byte.toString(HEX_RADIX).padStart(HEX_DIGITS_PER_BYTE, '0'))
    .join('')
    .slice(0, length);
}

/** UUID-shaped object IDs survive missing or throwing browser crypto APIs. */
export function createUuid(): string {
  return uuidWithReporting(true);
}

/** Diagnostic IDs cannot report crypto failures through the logger they identify. */
export function createDiagnosticUuid(): string {
  return uuidWithReporting(false);
}

function uuidWithReporting(reportFailures: boolean): string {
  return withCryptoFallback(
    'crypto.randomUUID',
    (webCrypto) => webCrypto.randomUUID(),
    'RandomText UUID',
    () => {
      const hex = randomTextWithReporting(UUID_RANDOM_TEXT_LENGTH, reportFailures).split('');
      hex[UUID_VERSION_INDEX] = UUID_VERSION_4;
      hex[UUID_VARIANT_INDEX] = (
        (Number.parseInt(hex[UUID_VARIANT_INDEX], HEX_RADIX) & UUID_VARIANT_MASK) |
        UUID_RFC_VARIANT
      ).toString(HEX_RADIX);
      const text = hex.join('');
      return `${text.slice(0, 8)}-${text.slice(8, 12)}-${text.slice(12, 16)}-${text.slice(16, 20)}-${text.slice(20)}`;
    },
    reportFailures
  );
}
