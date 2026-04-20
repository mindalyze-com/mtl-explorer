<template>
    <div style="display:none"></div>
</template>

<script lang="ts">
import {defineComponent, inject} from "vue";

const EVENTS = {
    locationUpdate: "locationUpdate",
    deviceEnabledDisabled: "deviceEnabledDisabled"
} as const;

export default defineComponent({
    name: 'LocateButton',
    components: {},
    data(): { watcherId: number | undefined } {
        return {
            watcherId: undefined,
        };
    },
    setup(): { toast: { add: (opts: { severity: string; summary: string; detail: string; life: number }) => void } } {
        return {
            toast: inject("toast") as { add: (opts: { severity: string; summary: string; detail: string; life: number }) => void },
        };
    },
    beforeUnmount() {
        if (this.watcherId !== undefined) {
            navigator.geolocation.clearWatch(this.watcherId);
            this.watcherId = undefined;
        }
    },
    emits: ['locationUpdate', 'deviceEnabledDisabled', 'tool-opened'],
    methods: {

        toggle() {
            this.locate();
            if (this.watcherId !== undefined) {
                this.$emit('tool-opened');
            }
        },

        close() {
            if (this.watcherId !== undefined) {
                navigator.geolocation.clearWatch(this.watcherId);
                this.watcherId = undefined;
                this.$emit('deviceEnabledDisabled', false);
            }
        },

        async locate() {
            try {


                const options = {
                    enableHighAccuracy: true,
                    timeout: 5000,
                    maximumAge: 0,
                };

                const success = (pos: GeolocationPosition) => {
                    const crd = pos.coords;
                    const long = crd.longitude;
                    const lat = crd.latitude;
                    console.log("Got location: " + crd + " pos(long/lat)=" + long + "/" + lat);

                    this.$emit(EVENTS.locationUpdate, pos);
                }

                function error(err: GeolocationPositionError) {
                    console.error(`ERROR(${err.code}): ${err.message}`);
                }

                if (this.watcherId === undefined) {
                    console.log("start GPS");
                    this.watcherId = navigator.geolocation.watchPosition(success, error, options);
                    this.$emit(EVENTS.deviceEnabledDisabled, true);
                    this.toast.add({severity: 'info', summary: 'Info', detail: 'GPS started', life: 2000});

                } else {
                    console.log("stop gps");
                    navigator.geolocation.clearWatch(this.watcherId);
                    this.watcherId = undefined;
                    this.$emit(EVENTS.deviceEnabledDisabled, false);
                    this.toast.add({severity: 'info', summary: 'Info', detail: 'GPS stopped', life: 2000});
                }


            } catch (error) {
                console.error('Error getting GPS location:', error);
                this.toast.add({severity: 'warning', summary: 'Info', detail: 'Unable to get GPS location', life: 2000});
            }
        },
    },
});
</script>

<style scoped>
</style>
