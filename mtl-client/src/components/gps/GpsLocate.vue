<template>
  <div style="display: none"></div>
</template>

<script setup lang="ts">
import { inject, onBeforeUnmount } from 'vue';

const GPS_TOAST_LIFE_MS = 4000;
const GEOLOCATION_ERROR_CODE = {
  permissionDenied: 1,
  positionUnavailable: 2,
  timeout: 3,
} as const;
const SECURE_CONTEXT_GPS_MESSAGE =
  'GPS needs HTTPS or localhost. Open MTL Explorer from a secure address to use live location.';

const EVENTS = {
  locationUpdate: 'locationUpdate',
  deviceEnabledDisabled: 'deviceEnabledDisabled',
} as const;

defineOptions({ name: 'LocateButton' });

type Emits = {
  (event: 'locationUpdate', position: GeolocationPosition): void;
  (event: 'deviceEnabledDisabled', enabled: boolean): void;
  (event: 'tool-opened'): void;
};

const emit = defineEmits<Emits>();

const toast = inject('toast') as {
  add: (opts: { severity: string; summary: string; detail: string; life: number }) => void;
};

let watcherId: number | undefined;
let gpsEnabled = false;
let activeWatchToken = 0;

onBeforeUnmount(() => {
  stopWatch(false);
});

function toggle() {
  locate();
}

function close() {
  stopWatch(false);
}

function isSecureLocationContext() {
  if (typeof window.isSecureContext === 'boolean') {
    return window.isSecureContext;
  }

  const secureLocalHostnames = new Set(['localhost', '127.0.0.1', '[::1]', '::1']);
  return window.location.protocol === 'https:' || secureLocalHostnames.has(window.location.hostname);
}

function showToast(severity: string, summary: string, detail: string) {
  toast.add({ severity, summary, detail, life: GPS_TOAST_LIFE_MS });
}

function confirmGpsStarted(position: GeolocationPosition) {
  if (!gpsEnabled) {
    gpsEnabled = true;
    emit(EVENTS.deviceEnabledDisabled, true);
    emit('tool-opened');
    showToast('info', 'GPS', 'GPS started');
  }

  emit(EVENTS.locationUpdate, position);
}

function describeGeolocationError(err: GeolocationPositionError) {
  if (!isSecureLocationContext() || err.message.toLowerCase().includes('secure origin')) {
    return SECURE_CONTEXT_GPS_MESSAGE;
  }

  if (err.code === GEOLOCATION_ERROR_CODE.permissionDenied) {
    return 'GPS permission was denied. Enable location access for this site and try again.';
  }

  if (err.code === GEOLOCATION_ERROR_CODE.positionUnavailable) {
    return 'GPS position is currently unavailable. Check device location services and try again.';
  }

  if (err.code === GEOLOCATION_ERROR_CODE.timeout) {
    return 'GPS timed out before a position was found. Move to a place with better signal and try again.';
  }

  return 'Unable to get GPS location.';
}

function stopWatch(showStoppedToast: boolean) {
  activeWatchToken += 1;
  const hadWatcher = watcherId !== undefined;
  if (watcherId !== undefined) {
    navigator.geolocation.clearWatch(watcherId);
    watcherId = undefined;
  }

  const wasEnabled = gpsEnabled;
  gpsEnabled = false;
  if (wasEnabled || hadWatcher) {
    emit(EVENTS.deviceEnabledDisabled, false);
  }
  if (showStoppedToast && wasEnabled) {
    showToast('info', 'GPS', 'GPS stopped');
  }
}

function handleGeolocationError(token: number, err: GeolocationPositionError) {
  if (token !== activeWatchToken) return;

  console.warn(`GPS unavailable (${err.code}): ${err.message}`);
  const detail = describeGeolocationError(err);
  stopWatch(false);
  showToast('warning', 'GPS unavailable', detail);
}

function locate() {
  try {
    const options: PositionOptions = {
      enableHighAccuracy: true,
      timeout: 5000,
      maximumAge: 0,
    };

    if (watcherId !== undefined) {
      console.log('stop gps');
      stopWatch(true);
      return;
    }

    if (!isSecureLocationContext()) {
      showToast('warning', 'GPS unavailable', SECURE_CONTEXT_GPS_MESSAGE);
      emit(EVENTS.deviceEnabledDisabled, false);
      return;
    }

    if (!navigator.geolocation) {
      showToast('warning', 'GPS unavailable', 'GPS is not available in this browser.');
      emit(EVENTS.deviceEnabledDisabled, false);
      return;
    }

    activeWatchToken += 1;
    const token = activeWatchToken;

    const success = (pos: GeolocationPosition) => {
      if (token !== activeWatchToken || watcherId === undefined) return;
      confirmGpsStarted(pos);
    };

    function error(err: GeolocationPositionError) {
      handleGeolocationError(token, err);
    }

    console.log('start GPS');
    watcherId = navigator.geolocation.watchPosition(success, error, options);
  } catch (error) {
    console.error('Error getting GPS location:', error);
    stopWatch(false);
    showToast('warning', 'GPS unavailable', 'Unable to get GPS location.');
  }
}

defineExpose({
  toggle,
  close,
});
</script>

<style scoped></style>
