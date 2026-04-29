<template>
  <div class="tab-content">

    <!-- Status row: loading / unavailable / available -->
    <div v-if="statusLoading" class="upload-notice upload-notice--info">
      <i class="pi pi-spin pi-spinner" /> Checking upload directory…
    </div>

    <div v-else-if="statusError" class="upload-notice upload-notice--error">
      <i class="pi pi-times-circle" /> {{ statusError }}
    </div>

    <div v-else-if="!status?.available" class="upload-notice upload-notice--warn">
      <i class="pi pi-exclamation-triangle" />
      <div>
        <strong>Upload unavailable</strong><br/>
        {{ status?.message }}<br/>
        <span class="action-hint" style="margin-top: 0.35rem; display: block;">
          Make sure the directory <code>{{ gpxUploadSubdir }}</code> inside your GPX folder exists
          and is writable by the server process. If the GPX volume is mounted read-only, remount
          it with write access.
        </span>
      </div>
    </div>

    <template v-else>
      <!-- Upload area -->
      <div
        class="drop-zone"
        :class="{ 'drop-zone--active': isDragging, 'drop-zone--disabled': uploading }"
        @dragover.prevent="isDragging = true"
        @dragleave.prevent="isDragging = false"
        @drop.prevent="onDrop"
        @click="!uploading && $refs.fileInput.click()"
      >
        <i class="pi pi-file-arrow-up drop-icon" />
        <span class="drop-label">
          {{ isDragging ? 'Drop GPX file here' : 'Click or drag a .gpx file here' }}
        </span>
        <input
          ref="fileInput"
          type="file"
          accept=".gpx"
          style="display: none"
          @change="onFileSelected"
        />
      </div>

      <!-- Selected file + upload button -->
      <div v-if="selectedFile" class="action-row" style="margin-top: 0.8rem;">
        <div class="action-info">
          <span class="action-label">{{ selectedFile.name }}</span>
          <span class="action-hint">{{ formatSize(selectedFile.size) }}</span>
        </div>
        <div class="action-controls">
          <Button
            label="Upload"
            icon="pi pi-upload"
            size="small"
            :disabled="uploading"
            :loading="uploading"
            @click="doUpload"
          />
        </div>
      </div>

      <!-- Result feedback -->
      <div v-if="uploadResult" class="upload-notice" :class="uploadResult.success ? 'upload-notice--success' : 'upload-notice--error'" style="margin-top: 0.6rem;">
        <i :class="uploadResult.success ? 'pi pi-check-circle' : 'pi pi-times-circle'" />
        {{ uploadResult.message }}
      </div>

      <span class="action-hint" style="margin-top: 0.8rem;">
        Files are saved to <code>{{ gpxUploadSubdir }}</code> inside the GPX folder and automatically indexed.
      </span>
    </template>
  </div>
</template>

<script>
import { defineComponent } from 'vue';
import { getGpxUploadStatus, uploadGpxFile } from '@/utils/ServiceHelper';

const GPX_UPLOAD_SUBDIR = 'GPX-UPLOAD';

export default defineComponent({
  name: 'GpxUploadTab',
  data() {
    return {
      statusLoading: false,
      statusError: '',
      status: null,
      selectedFile: null,
      isDragging: false,
      uploading: false,
      uploadResult: null,
      gpxUploadSubdir: GPX_UPLOAD_SUBDIR,
    };
  },
  methods: {
    async loadStatus() {
      this.statusLoading = true;
      this.statusError = '';
      this.status = null;
      try {
        this.status = await getGpxUploadStatus();
      } catch (e) {
        this.statusError = 'Could not reach server: ' + (e?.message || e);
      } finally {
        this.statusLoading = false;
      }
    },
    onDrop(event) {
      this.isDragging = false;
      const file = event.dataTransfer?.files?.[0];
      if (file) this.setFile(file);
    },
    onFileSelected(event) {
      const file = event.target?.files?.[0];
      if (file) this.setFile(file);
    },
    setFile(file) {
      this.uploadResult = null;
      if (!file.name.toLowerCase().endsWith('.gpx')) {
        this.uploadResult = { success: false, message: 'Only .gpx files are accepted.' };
        return;
      }
      this.selectedFile = file;
    },
    async doUpload() {
      if (!this.selectedFile) return;
      this.uploading = true;
      this.uploadResult = null;
      try {
        const result = await uploadGpxFile(this.selectedFile);
        this.uploadResult = result;
        if (result.success) {
          this.selectedFile = null;
          if (this.$refs.fileInput) this.$refs.fileInput.value = '';
        }
      } catch (e) {
        this.uploadResult = { success: false, message: 'Upload failed: ' + (e?.message || e) };
      } finally {
        this.uploading = false;
      }
    },
    formatSize(bytes) {
      if (bytes < 1024) return bytes + ' B';
      if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
      return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    },
  },
});
</script>

<style scoped>
.drop-zone {
  border: 2px dashed var(--border-subtle);
  border-radius: 8px;
  padding: 2rem 1rem;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.drop-zone:hover:not(.drop-zone--disabled),
.drop-zone--active {
  border-color: var(--accent);
  background: var(--accent-bg);
}

.drop-zone--disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.drop-icon {
  font-size: var(--text-3xl-size);
  color: var(--text-secondary);
}

.drop-label {
  font-size: var(--text-sm-size);
  color: var(--text-secondary);
}

.upload-notice {
  display: flex;
  align-items: flex-start;
  gap: 0.6rem;
  padding: 0.7rem 0.9rem;
  border-radius: 6px;
  font-size: var(--text-sm-size);
  line-height: var(--text-sm-lh);
  margin-top: 0.6rem;
}

.upload-notice--info  { background: var(--accent-bg); }
.upload-notice--warn  { background: var(--warning-bg); color: var(--warning-text); }
.upload-notice--error { background: var(--error-bg); color: var(--error); }
.upload-notice--success { background: var(--success-bg); color: var(--success); }
</style>
