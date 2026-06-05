import { mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import GpsLocate from '@/components/gps/GpsLocate.vue';

type GpsLocateExpose = {
  toggle: () => void;
  close: () => void;
};

type GeolocationCallbacks = {
  error?: PositionErrorCallback;
  success?: PositionCallback;
};

const GPS_WATCH_ID = 42;

const originalGeolocation = Object.getOwnPropertyDescriptor(navigator, 'geolocation');
const originalSecureContext = Object.getOwnPropertyDescriptor(window, 'isSecureContext');

function restoreProperty(target: object, key: string, descriptor: PropertyDescriptor | undefined) {
  if (descriptor) {
    Object.defineProperty(target, key, descriptor);
  } else {
    delete (target as Record<string, unknown>)[key];
  }
}

function setSecureContext(value: boolean) {
  Object.defineProperty(window, 'isSecureContext', {
    configurable: true,
    value,
  });
}

function installGeolocationMock(callbacks: GeolocationCallbacks = {}) {
  const watchPosition = vi.fn((success: PositionCallback, error?: PositionErrorCallback) => {
    callbacks.success = success;
    callbacks.error = error;
    return GPS_WATCH_ID;
  });
  const clearWatch = vi.fn();

  Object.defineProperty(navigator, 'geolocation', {
    configurable: true,
    value: {
      clearWatch,
      watchPosition,
    },
  });

  return { clearWatch, watchPosition };
}

function mountGpsLocate() {
  const toastAdd = vi.fn();
  const wrapper = mount(GpsLocate, {
    global: {
      provide: {
        toast: { add: toastAdd },
      },
    },
  });

  return { toastAdd, wrapper };
}

function toggle(wrapper: ReturnType<typeof mountGpsLocate>['wrapper']) {
  (wrapper.vm as unknown as GpsLocateExpose).toggle();
}

function geolocationError(code: number, message: string): GeolocationPositionError {
  return {
    code,
    message,
    PERMISSION_DENIED: 1,
    POSITION_UNAVAILABLE: 2,
    TIMEOUT: 3,
  } as GeolocationPositionError;
}

function geolocationPosition(): GeolocationPosition {
  return {
    coords: {
      accuracy: 5,
      altitude: null,
      altitudeAccuracy: null,
      heading: null,
      latitude: 47.3769,
      longitude: 8.5417,
      speed: null,
    },
    timestamp: 123456789,
  };
}

describe('GpsLocate', () => {
  beforeEach(() => {
    vi.spyOn(console, 'log').mockImplementation(() => {});
    vi.spyOn(console, 'warn').mockImplementation(() => {});
    vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    restoreProperty(navigator, 'geolocation', originalGeolocation);
    restoreProperty(window, 'isSecureContext', originalSecureContext);
    vi.restoreAllMocks();
  });

  it('does not start GPS on a plain HTTP non-secure context', () => {
    setSecureContext(false);
    const { watchPosition } = installGeolocationMock();
    const { toastAdd, wrapper } = mountGpsLocate();

    toggle(wrapper);

    expect(watchPosition).not.toHaveBeenCalled();
    expect(wrapper.emitted('deviceEnabledDisabled')).toEqual([[false]]);
    expect(toastAdd).toHaveBeenCalledWith(
      expect.objectContaining({
        detail: expect.stringContaining('HTTPS or localhost'),
        severity: 'warning',
        summary: 'GPS unavailable',
      })
    );
    expect(toastAdd).not.toHaveBeenCalledWith(expect.objectContaining({ detail: 'GPS started' }));
  });

  it('rolls back pending GPS when Chrome rejects geolocation asynchronously', () => {
    setSecureContext(true);
    const callbacks: GeolocationCallbacks = {};
    const { clearWatch, watchPosition } = installGeolocationMock(callbacks);
    const { toastAdd, wrapper } = mountGpsLocate();

    toggle(wrapper);

    expect(watchPosition).toHaveBeenCalledOnce();
    expect(wrapper.emitted('deviceEnabledDisabled')).toBeUndefined();
    expect(toastAdd).not.toHaveBeenCalledWith(expect.objectContaining({ detail: 'GPS started' }));

    callbacks.error?.(geolocationError(1, 'Only secure origins are allowed'));

    expect(clearWatch).toHaveBeenCalledWith(GPS_WATCH_ID);
    expect(wrapper.emitted('deviceEnabledDisabled')).toEqual([[false]]);
    expect(toastAdd).toHaveBeenCalledWith(
      expect.objectContaining({
        detail: expect.stringContaining('HTTPS or localhost'),
        severity: 'warning',
        summary: 'GPS unavailable',
      })
    );
  });

  it('marks GPS started only after the first position arrives', () => {
    setSecureContext(true);
    const callbacks: GeolocationCallbacks = {};
    const { clearWatch } = installGeolocationMock(callbacks);
    const { toastAdd, wrapper } = mountGpsLocate();
    const position = geolocationPosition();

    toggle(wrapper);

    expect(wrapper.emitted('deviceEnabledDisabled')).toBeUndefined();
    expect(wrapper.emitted('locationUpdate')).toBeUndefined();

    callbacks.success?.(position);

    expect(wrapper.emitted('deviceEnabledDisabled')).toEqual([[true]]);
    expect(wrapper.emitted('locationUpdate')).toEqual([[position]]);
    expect(toastAdd).toHaveBeenCalledWith(
      expect.objectContaining({
        detail: 'GPS started',
        severity: 'info',
        summary: 'GPS',
      })
    );

    toggle(wrapper);

    expect(clearWatch).toHaveBeenCalledWith(GPS_WATCH_ID);
    expect(wrapper.emitted('deviceEnabledDisabled')).toEqual([[true], [false]]);
    expect(toastAdd).toHaveBeenCalledWith(
      expect.objectContaining({
        detail: 'GPS stopped',
        severity: 'info',
        summary: 'GPS',
      })
    );
  });
});
