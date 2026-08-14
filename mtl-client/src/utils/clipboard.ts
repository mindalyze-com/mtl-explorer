export function selectInputText(event: Event): void {
  (event.target as HTMLInputElement | null)?.select?.();
}

export function copyTextWithFallback(text: string): void {
  const textArea = document.createElement('textarea');
  textArea.value = text;
  textArea.setAttribute('readonly', '');
  textArea.style.position = 'fixed';
  textArea.style.opacity = '0';
  document.body.appendChild(textArea);

  try {
    textArea.select();
    if (!document.execCommand('copy')) {
      throw new Error('Browser copy command failed');
    }
  } finally {
    textArea.remove();
  }
}

export async function writeTextToClipboard(text: string): Promise<void> {
  let clipboardError: unknown = null;
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return;
    } catch (error) {
      clipboardError = error;
    }
  }

  try {
    copyTextWithFallback(text);
  } catch (fallbackError) {
    throw clipboardError ?? fallbackError;
  }
}
