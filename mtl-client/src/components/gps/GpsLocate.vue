<template>
  <div style="display: none"></div>
</template>

<script setup lang="ts">
import { inject, onBeforeUnmount } from 'vue';

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

onBeforeUnmount(() => {
  if (watcherId !== undefined) {
    navigator.geolocation.clearWatch(watcherId);
    watcherId = undefined;
  }
});

function toggle() {
  void locate();
  if (watcherId !== undefined) {
    emit('tool-opened');
  }
}

function close() {
  if (watcherId !== undefined) {
    navigator.geolocation.clearWatch(watcherId);
    watcherId = undefined;
    emit('deviceEnabledDisabled', false);
  }
}

async function locate() {
  try {
    const options: PositionOptions = {
      enableHighAccuracy: true,
      timeout: 5000,
      maximumAge: 0,
    };

    const success = (pos: GeolocationPosition) => {
      emit(EVENTS.locationUpdate, pos);
    };

    function error(err: GeolocationPositionError) {
      console.error(`ERROR(${err.code}): ${err.message}`);
    }

    if (watcherId === undefined) {
      console.log('start GPS');
      watcherId = navigator.geolocation.watchPosition(success, error, options);
      emit(EVENTS.deviceEnabledDisabled, true);
      toast.add({ severity: 'info', summary: 'Info', detail: 'GPS started', life: 2000 });
    } else {
      console.log('stop gps');
      navigator.geolocation.clearWatch(watcherId);
      watcherId = undefined;
      emit(EVENTS.deviceEnabledDisabled, false);
      toast.add({ severity: 'info', summary: 'Info', detail: 'GPS stopped', life: 2000 });
    }
  } catch (error) {
    console.error('Error getting GPS location:', error);
    toast.add({ severity: 'warning', summary: 'Info', detail: 'Unable to get GPS location', life: 2000 });
  }
}

defineExpose({
  toggle,
  close,
});
</script>

<style scoped></style>
