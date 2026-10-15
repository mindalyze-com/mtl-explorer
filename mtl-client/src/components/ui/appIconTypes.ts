/** Class bindings accepted by the shared navigation icon. */
export type AppIconClass =
  string | Record<string, unknown> | (AppIconClass | null | undefined | false)[] | null | undefined | false;

export interface AppIconStatusDot {
  tone?: 'notice' | 'warning' | 'danger' | 'success';
  pulse?: boolean;
  /** Optional glyph for this status, such as a filled filter icon. */
  icon?: AppIconClass;
}

export interface AppIconProps {
  icon: AppIconClass;
  statusDot?: AppIconStatusDot | boolean;
  /** Omit for decorative icons; label the enclosing control instead. */
  label?: string;
}
