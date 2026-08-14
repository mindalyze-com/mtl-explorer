export type ToastMessage = {
  severity?: string;
  summary?: string;
  detail?: string;
  life?: number;
  [key: string]: unknown;
};

export type ToastService = {
  add: (message: ToastMessage) => void;
};
