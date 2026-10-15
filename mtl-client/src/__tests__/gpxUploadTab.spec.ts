import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { createPinia } from 'pinia';

import GpxUploadTab from '@/components/admin/GpxUploadTab.vue';
import { getGpxUploadStatus, uploadGpxFile } from '@/utils/ServiceHelper';

vi.mock('@/utils/ServiceHelper', () => ({
  getGpxUploadStatus: vi.fn(),
  uploadGpxFile: vi.fn(),
}));

const buttonStub = {
  props: ['disabled', 'icon', 'label', 'loading', 'size'],
  emits: ['click'],
  template: '<button type="button" :disabled="disabled" @click="$emit(\'click\')">{{ label }}</button>',
};

async function mountAvailableUploadTab(demoMode?: boolean | null) {
  vi.mocked(getGpxUploadStatus).mockResolvedValue({
    available: true,
    message: 'Upload directory is available.',
  });

  const wrapper = mount(GpxUploadTab, {
    props: { demoMode },
    global: {
      plugins: [createPinia()],
      stubs: {
        Button: buttonStub,
        RouterLink: { name: 'RouterLink', props: ['to'], template: '<a><slot /></a>' },
      },
    },
  });

  await (wrapper.vm as unknown as { loadStatus: () => Promise<void> }).loadStatus();
  await flushPromises();
  return wrapper;
}

async function dropFile(wrapper: ReturnType<typeof mount>, file: File) {
  await wrapper.find('.drop-zone').trigger('drop', {
    dataTransfer: {
      files: [file],
    },
  });
  await flushPromises();
}

describe('GpxUploadTab', () => {
  beforeEach(() => {
    vi.mocked(getGpxUploadStatus).mockReset();
    vi.mocked(uploadGpxFile).mockReset();
  });

  it('rejects zero-byte track files with a clear message and clears the selected file', async () => {
    const wrapper = await mountAvailableUploadTab();

    await dropFile(wrapper, new File(['<gpx></gpx>'], 'valid.gpx'));
    expect(wrapper.text()).toContain('valid.gpx');
    expect(wrapper.find('.selected-file-row button').exists()).toBe(true);

    await dropFile(wrapper, new File([], 'empty.gpx'));

    expect(wrapper.text()).toContain('Selected file is empty. Choose a non-empty GPS track file.');
    expect(wrapper.text()).not.toContain('valid.gpx');
    expect(wrapper.find('.selected-file-row button').exists()).toBe(false);
    expect(uploadGpxFile).not.toHaveBeenCalled();
  });

  it('waits for installation access before offering uploads and reacts to the confirmed mode', async () => {
    const wrapper = await mountAvailableUploadTab(null);
    expect(wrapper.text()).toContain('Checking upload access…');
    expect(wrapper.find('.drop-zone').exists()).toBe(false);
    expect(wrapper.find('input[type="file"]').exists()).toBe(false);

    await wrapper.setProps({ demoMode: true });
    expect(wrapper.text()).toContain('Track uploads are unavailable in this shared demo.');
    expect(wrapper.find('.drop-zone').exists()).toBe(false);
    expect(uploadGpxFile).not.toHaveBeenCalled();

    await wrapper.setProps({ demoMode: null });
    expect(wrapper.text()).toContain('Checking upload access…');
    await wrapper.setProps({ demoMode: false });
    expect(wrapper.find('.drop-zone').exists()).toBe(true);
    vi.mocked(uploadGpxFile).mockResolvedValue({ success: false, message: 'Synthetic validation response.' });
    const file = new File(['<gpx/>'], 'synthetic.gpx');
    await dropFile(wrapper, file);
    await wrapper.get('.selected-file-row button').trigger('click');
    await flushPromises();
    expect(uploadGpxFile).toHaveBeenCalledExactlyOnceWith(file);
    wrapper.unmount();
  });

  it('shows the per-file server rejection when a GPX has no track points', async () => {
    vi.mocked(uploadGpxFile).mockResolvedValue({
      success: false,
      message: 'Uploaded GPX does not contain any track points. No track was imported.',
    });
    const wrapper = await mountAvailableUploadTab();
    const waypointOnly = new File(
      [
        '<gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">',
        '<wpt lat="46.9480" lon="7.4470"><name>Not a track</name></wpt>',
        '</gpx>',
      ],
      'waypoint-only.gpx'
    );

    await dropFile(wrapper, waypointOnly);
    await wrapper.get('.selected-file-row button').trigger('click');
    await flushPromises();

    expect(uploadGpxFile).toHaveBeenCalledWith(waypointOnly);
    expect(wrapper.get('.upload-notice--error').text()).toContain(
      'Uploaded GPX does not contain any track points. No track was imported.'
    );
    expect(wrapper.text()).toContain('waypoint-only.gpx');
  });

  it('links a successful upload to its server file name in review', async () => {
    vi.mocked(uploadGpxFile).mockResolvedValue({
      success: true,
      fileName: 'new_track.gpx',
      message: 'Uploaded; indexing will begin shortly.',
    });
    const wrapper = await mountAvailableUploadTab();
    await dropFile(wrapper, new File(['<gpx/>'], 'new track.gpx'));
    await wrapper.get('.selected-file-row button').trigger('click');
    await flushPromises();
    expect(wrapper.get('.upload-review-link').text()).toBe('Review uploaded recording');
    expect(wrapper.getComponent({ name: 'RouterLink' }).props('to')).toEqual({
      name: 'review',
      query: { file: 'new_track.gpx' },
    });
    await dropFile(wrapper, new File(['<gpx/>'], 'next.gpx'));
    expect(wrapper.find('.upload-review-link').exists()).toBe(false);
    wrapper.unmount();
  });
});
