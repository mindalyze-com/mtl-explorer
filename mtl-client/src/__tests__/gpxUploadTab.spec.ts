import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import GpxUploadTab from '@/components/admin/GpxUploadTab.vue';
import { getGpxUploadStatus, uploadGpxFile } from '@/utils/ServiceHelper';

vi.mock('@/utils/ServiceHelper', () => ({
  getGpxUploadStatus: vi.fn(),
  uploadGpxFile: vi.fn(),
}));

const buttonStub = {
  props: ['disabled', 'icon', 'label', 'loading', 'size'],
  template: '<button type="button" :disabled="disabled" @click="$emit(\'click\')">{{ label }}</button>',
};

async function mountAvailableUploadTab() {
  vi.mocked(getGpxUploadStatus).mockResolvedValue({
    available: true,
    message: 'Upload directory is available.',
  });

  const wrapper = mount(GpxUploadTab, {
    global: {
      stubs: {
        Button: buttonStub,
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
    expect(wrapper.find('button').exists()).toBe(true);

    await dropFile(wrapper, new File([], 'empty.gpx'));

    expect(wrapper.text()).toContain('Selected file is empty. Choose a non-empty GPS track file.');
    expect(wrapper.text()).not.toContain('valid.gpx');
    expect(wrapper.find('button').exists()).toBe(false);
    expect(uploadGpxFile).not.toHaveBeenCalled();
  });
});
