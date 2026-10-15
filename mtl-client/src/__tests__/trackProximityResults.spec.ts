import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import { TrackProcessingStatusDtoStateEnum, type TrackProcessingStatusDto } from 'x8ing-mtl-api-typescript-fetch';
import TrackProximityResults from '@/components/map/TrackProximityResults.vue';

const processing: TrackProcessingStatusDto = {
  state: TrackProcessingStatusDtoStateEnum.Running,
  stages: [],
  failedGpsFiles: 0,
};
const track = { id: 42, name: 'Synthetic ride', displayName: 'Synthetic ride', description: '', date: '02/10/2026' };

function render(tracks = [track], status = processing) {
  return mount(TrackProximityResults, {
    props: { tracks, processing: status },
    global: { stubs: { TrackShapePreview: true, ActivityTypeBadge: true } },
  });
}

describe('nearby track processing notices', () => {
  it('warns for empty searches while processing is running', () => {
    const wrapper = render([]);
    expect(wrapper.get('[role="status"]').text()).toBe(
      'No matching tracks found yet. Processing is still running, so results may be incomplete.'
    );
    expect(wrapper.find('button').exists()).toBe(false);
  });

  it('shows the warning above selectable partial results', async () => {
    const wrapper = render();
    expect(wrapper.get('[role="status"]').text()).toBe(
      'Tracks are still being processed. Some nearby tracks may be missing.'
    );
    await wrapper.get('button').trigger('click');
    expect(wrapper.emitted('select')).toEqual([[42]]);
  });

  it('reports failure separately without claiming processing is running', () => {
    const wrapper = render([], { ...processing, state: TrackProcessingStatusDtoStateEnum.Ready, failedGpsFiles: 2 });
    expect(wrapper.text()).toContain('2 GPS files could not be processed');
    expect(wrapper.text()).toContain('No matching tracks found.');
    expect(wrapper.text()).not.toContain('still running');
  });

  it('shows unavailable status even with no matches', () => {
    const wrapper = render([], { ...processing, state: TrackProcessingStatusDtoStateEnum.Unavailable });
    expect(wrapper.text()).toContain('Processing status is unavailable. Nearby results may be incomplete.');
  });

  it('clears notices when a later response is ready', async () => {
    const wrapper = render();
    await wrapper.setProps({ processing: { ...processing, state: TrackProcessingStatusDtoStateEnum.Ready } });
    expect(wrapper.find('[role="status"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('Synthetic ride');
  });
});
