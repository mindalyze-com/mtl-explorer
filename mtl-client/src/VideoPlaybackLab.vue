<template>
  <main class="lab">
    <header class="lab__header">
      <div>
        <p class="lab__eyebrow">Local test page</p>
        <h1>MTL Explorer video playback lab</h1>
        <p class="lab__intro">
          Ten newly generated videos are served by the local MTL Explorer server. No compatible stream has been
          prepared yet.
        </p>
      </div>

      <aside class="lab__instructions" aria-label="Test instructions">
        <strong>Try the fallback flow</strong>
        <ol>
          <li>Choose a quality on one orange card.</li>
          <li>Select <em>Create compatible stream</em>.</li>
          <li>Watch the percentage and encode speed.</li>
        </ol>
        <p>Start one fallback at a time. The local server allows one active encode by default.</p>
      </aside>
    </header>

    <section class="lab__section" aria-labelledby="native-heading">
      <div class="lab__section-heading">
        <div>
          <p class="lab__kicker lab__kicker--native"><span></span> Browser-native</p>
          <h2 id="native-heading">Plays the original file</h2>
        </div>
        <p>Five 60-second videos. Press play; the server should not create a transcode session.</p>
      </div>

      <div class="lab__grid">
        <article v-for="item in nativeVideos" :key="item.id" class="video-card video-card--native">
          <div class="video-card__stage">
            <CompatibleVideoPlayer
              :media-id="item.id"
              :src="mediaContentUrl(item.id)"
              :label="item.name"
            />
          </div>
          <div class="video-card__body">
            <span class="video-card__number">{{ item.number }}</span>
            <div>
              <h3>{{ item.name }}</h3>
              <p>{{ item.container }} · {{ item.codec }} · AAC · 480p · 1:00</p>
            </div>
          </div>
        </article>
      </div>
    </section>

    <section class="lab__section" aria-labelledby="fallback-heading">
      <div class="lab__section-heading">
        <div>
          <p class="lab__kicker lab__kicker--fallback"><span></span> Compatible stream required</p>
          <h2 id="fallback-heading">Exercises server transcoding</h2>
        </div>
        <p>Five 5-minute videos. This Chromium build rejected every original with media error 4.</p>
      </div>

      <div class="lab__grid">
        <article v-for="item in fallbackVideos" :key="item.id" class="video-card video-card--fallback">
          <div class="video-card__stage">
            <CompatibleVideoPlayer
              :media-id="item.id"
              :src="mediaContentUrl(item.id)"
              :label="item.name"
            />
          </div>
          <div class="video-card__body">
            <span class="video-card__number">{{ item.number }}</span>
            <div>
              <h3>{{ item.name }}</h3>
              <p>{{ item.container }} · {{ item.codec }} · MP3/MP2 · 720p · 5:00</p>
            </div>
          </div>
        </article>
      </div>
    </section>

    <footer class="lab__footer">
      <p>
        If media requests are rejected, <a href="/mtl/" target="_blank" rel="noreferrer">sign in to the normal local client</a>
        and reload this page.
      </p>
      <p>These fixtures and all compatible streams use an isolated temporary directory.</p>
    </footer>
  </main>
</template>

<script setup lang="ts">
import { onMounted } from 'vue';
import CompatibleVideoPlayer from '@/components/map/CompatibleVideoPlayer.vue';
import { mediaContentUrl } from '@/repositories/mediaRepository';

type LabVideo = {
  id: number;
  number: string;
  name: string;
  container: string;
  codec: string;
};

const nativeVideos: LabVideo[] = [
  { id: 400028, number: '01', name: 'H.264 in MP4', container: 'MP4', codec: 'H.264' },
  { id: 400034, number: '02', name: 'H.264 in MOV', container: 'MOV', codec: 'H.264' },
  { id: 400035, number: '03', name: 'H.264 in M4V', container: 'M4V', codec: 'H.264' },
  { id: 400027, number: '04', name: 'VP9 in MP4', container: 'MP4', codec: 'VP9' },
  { id: 400026, number: '05', name: 'AV1 in MP4', container: 'MP4', codec: 'AV1' },
];

const fallbackVideos: LabVideo[] = [
  { id: 400029, number: '01', name: 'MPEG-2 in AVI', container: 'AVI', codec: 'MPEG-2' },
  { id: 400032, number: '02', name: 'MPEG-4 Part 2 in AVI', container: 'AVI', codec: 'MPEG-4 Part 2' },
  { id: 400031, number: '03', name: 'MS MPEG-4 v3 in AVI', container: 'AVI', codec: 'MS MPEG-4 v3' },
  { id: 400033, number: '04', name: 'MS MPEG-4 v2 in AVI', container: 'AVI', codec: 'MS MPEG-4 v2' },
  { id: 400030, number: '05', name: 'WMV2 in AVI', container: 'AVI', codec: 'WMV2' },
];

onMounted(() => {
  document.querySelectorAll<HTMLVideoElement>('.lab video').forEach((video) => {
    video.defaultMuted = true;
    video.muted = true;
  });
});
</script>

<style>
html {
  color-scheme: dark;
  background: #070b12;
}

body {
  min-width: 320px;
  margin: 0;
  color: #e7ecf4;
  background:
    radial-gradient(circle at 10% 0%, rgba(99, 102, 241, 0.16), transparent 30rem),
    radial-gradient(circle at 92% 20%, rgba(8, 145, 178, 0.12), transparent 34rem),
    #070b12;
}

button,
select {
  font: inherit;
}

.lab {
  --mp-shell: #070b12;
  --mp-surface: #0b111b;
  --mp-stage: #070b12;
  --mp-text-strong: rgba(255, 255, 255, 0.94);
  --mp-text: rgba(255, 255, 255, 0.78);
  --mp-text-muted: rgba(255, 255, 255, 0.58);
  --mp-border: rgba(255, 255, 255, 0.1);
  --mp-border-strong: rgba(255, 255, 255, 0.18);
  --mp-border-emphasis: rgba(255, 255, 255, 0.32);
  --mp-control-bg: rgba(255, 255, 255, 0.07);
  --mp-control-hover: rgba(255, 255, 255, 0.12);
  --mp-control-active: rgba(255, 255, 255, 0.15);
  --mp-overlay-shadow: 0 8px 24px rgba(0, 0, 0, 0.36);
  width: min(1520px, calc(100% - 2rem));
  margin: 0 auto;
  padding: 3rem 0 4rem;
}

.lab__header {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(18rem, 0.7fr);
  gap: 2rem;
  align-items: end;
  padding: 0 0.5rem 2.5rem;
  border-bottom: 1px solid rgba(255, 255, 255, 0.11);
}

.lab__eyebrow,
.lab__kicker {
  margin: 0 0 0.5rem;
  color: #9ea8b8;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.13em;
  text-transform: uppercase;
}

.lab h1 {
  max-width: 17ch;
  margin: 0;
  font-size: clamp(2.3rem, 5vw, 4.8rem);
  line-height: 0.98;
  letter-spacing: -0.05em;
}

.lab__intro {
  max-width: 48rem;
  margin: 1.25rem 0 0;
  color: #aab5c6;
  font-size: 1.05rem;
  line-height: 1.6;
}

.lab__instructions {
  padding: 1.1rem 1.25rem;
  color: #c8d1df;
  background: rgba(255, 255, 255, 0.045);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 12px;
}

.lab__instructions strong {
  color: #fff;
}

.lab__instructions ol {
  margin: 0.65rem 0;
  padding-left: 1.2rem;
  line-height: 1.6;
}

.lab__instructions p {
  margin: 0;
  color: #94a0b3;
  font-size: 0.82rem;
  line-height: 1.45;
}

.lab__section {
  padding: 3rem 0 1rem;
}

.lab__section-heading {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 2rem;
  margin: 0 0 1.25rem;
  padding: 0 0.5rem;
}

.lab__section-heading h2 {
  margin: 0;
  font-size: clamp(1.5rem, 3vw, 2.15rem);
  letter-spacing: -0.025em;
}

.lab__section-heading > p {
  max-width: 34rem;
  margin: 0;
  color: #8f9bad;
  line-height: 1.5;
  text-align: right;
}

.lab__kicker {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.lab__kicker span {
  width: 0.55rem;
  height: 0.55rem;
  border-radius: 50%;
}

.lab__kicker--native span {
  background: #34d399;
  box-shadow: 0 0 0 4px rgba(52, 211, 153, 0.12);
}

.lab__kicker--fallback span {
  background: #f59e0b;
  box-shadow: 0 0 0 4px rgba(245, 158, 11, 0.12);
}

.lab__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 27rem), 1fr));
  gap: 1rem;
}

.video-card {
  overflow: hidden;
  background: rgba(11, 17, 27, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.11);
  border-radius: 14px;
  box-shadow: 0 12px 34px rgba(0, 0, 0, 0.18);
}

.video-card--native {
  border-top-color: rgba(52, 211, 153, 0.58);
}

.video-card--fallback {
  border-top-color: rgba(245, 158, 11, 0.68);
}

.video-card__stage {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.025) 25%, transparent 25%) 0 0 / 24px 24px,
    #05080d;
}

.video-card__stage .mp__media {
  width: 100%;
  height: 100%;
}

.video-card__body {
  display: flex;
  gap: 0.85rem;
  align-items: start;
  padding: 0.9rem 1rem 1rem;
}

.video-card__number {
  display: grid;
  flex: 0 0 auto;
  width: 2rem;
  height: 2rem;
  place-items: center;
  color: #acb7c7;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 7px;
  font-size: 0.72rem;
  font-weight: 700;
}

.video-card h3 {
  margin: 0.05rem 0 0.28rem;
  color: #f5f7fa;
  font-size: 1rem;
}

.video-card p {
  margin: 0;
  color: #8996aa;
  font-size: 0.78rem;
  line-height: 1.4;
}

.lab__footer {
  display: flex;
  justify-content: space-between;
  gap: 2rem;
  margin-top: 2rem;
  padding: 1.5rem 0.5rem 0;
  color: #7f8a9b;
  border-top: 1px solid rgba(255, 255, 255, 0.1);
  font-size: 0.82rem;
}

.lab__footer p {
  margin: 0;
}

.lab a {
  color: #9fb5ff;
}

@media (max-width: 820px) {
  .lab__header {
    grid-template-columns: 1fr;
    align-items: start;
  }

  .lab__section-heading,
  .lab__footer {
    align-items: start;
    flex-direction: column;
    gap: 0.75rem;
  }

  .lab__section-heading > p {
    text-align: left;
  }
}
</style>
