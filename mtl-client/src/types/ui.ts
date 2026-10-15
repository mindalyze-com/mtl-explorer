export type NotificationAction = {
  label: string;
  run: () => void;
  primary?: boolean;
};

export type ToastMessage = {
  severity?: string;
  summary?: string;
  detail?: string;
  life?: number;
  icon?: string;
  closable?: boolean;
  actions?: NotificationAction[];
  onClose?: () => void;
  [key: string]: unknown;
};

export type ToastService = {
  add: (message: ToastMessage) => void;
};
