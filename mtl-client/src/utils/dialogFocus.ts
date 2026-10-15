/** Focus after the modal transition, without the browser's competing autofocus. */
export function focusDialogInput(dialog: HTMLElement): void {
  dialog.querySelector<HTMLInputElement>('input')?.focus({ preventScroll: true });
}
