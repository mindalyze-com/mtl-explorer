import { mount } from '@vue/test-utils';
import { Comment, Fragment, defineComponent, h, nextTick, type VNode, type VNodeChild } from 'vue';
import { describe, expect, it } from 'vitest';
import type { GpsTrack } from 'x8ing-mtl-api-typescript-fetch/dist/esm/models/index';
import TrackIdParam from '@/components/filter/TrackIdParam.vue';

function flattenVNodes(nodes: VNode[]): VNode[] {
  return nodes.flatMap((node) => {
    if (node.type === Comment) return [];
    if (node.type === Fragment && Array.isArray(node.children)) return flattenVNodes(node.children as VNode[]);
    return [node];
  });
}

function valueForField(row: Record<string, unknown>, field: string): unknown {
  return field.split('.').reduce<unknown>((value, key) => {
    if (value && typeof value === 'object') return (value as Record<string, unknown>)[key];
    return undefined;
  }, row);
}

const DialogStub = defineComponent({
  name: 'Dialog',
  props: {
    visible: { type: Boolean, default: false },
  },
  setup(props, { slots }) {
    return () => (props.visible ? h('section', { 'data-test': 'dialog' }, slots.default?.()) : null);
  },
});

const DataTableStub = defineComponent({
  name: 'DataTable',
  props: {
    value: { type: Array, default: () => [] },
  },
  setup(props, { slots }) {
    return () => {
      const columns = flattenVNodes((slots.default?.() ?? []) as VNode[]);
      const rows = props.value as Record<string, unknown>[];

      return h(
        'div',
        { 'data-test': 'data-table' },
        rows.map((row, rowIndex) =>
          h(
            'div',
            { 'data-test': 'row', key: rowIndex },
            columns.map((column, columnIndex) => {
              const columnProps = (column.props ?? {}) as Record<string, unknown>;
              const columnSlots = (column.children ?? {}) as Record<string, unknown>;
              const bodySlot = columnSlots.body;
              const content: VNodeChild =
                typeof bodySlot === 'function'
                  ? (bodySlot({ data: row, index: rowIndex }) as VNodeChild)
                  : String(valueForField(row, String(columnProps.field ?? '')) ?? '');

              return h('div', { 'data-test': 'cell', key: columnIndex }, content);
            })
          )
        )
      );
    };
  },
});

const ColumnStub = defineComponent({
  name: 'Column',
  props: {
    field: { type: String, default: '' },
    header: { type: String, default: '' },
    sortable: { type: Boolean, default: false },
  },
  setup() {
    return () => null;
  },
});

const InputTextStub = defineComponent({
  name: 'InputText',
  props: {
    modelValue: { type: String, default: '' },
    placeholder: { type: String, default: '' },
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    return () =>
      h('input', {
        'data-test': 'search',
        value: props.modelValue,
        placeholder: props.placeholder,
        onInput: (event: Event) => emit('update:modelValue', (event.target as HTMLInputElement).value),
      });
  },
});

const TrackShapePreviewStub = defineComponent({
  name: 'TrackShapePreview',
  props: {
    trackId: { type: Number, required: true },
  },
  template: '<span data-test="shape-preview" :data-track-id="trackId" />',
});

function track(id: number, name: string, startDate: Date): GpsTrack {
  return {
    id,
    trackName: name,
    startDate,
  } as GpsTrack;
}

describe('TrackIdParam', () => {
  it('renders route shape previews in the selected-track chooser', async () => {
    const wrapper = mount(TrackIdParam, {
      props: {
        modelValue: '',
        tracks: [
          track(7, 'Older track', new Date('2026-06-01T10:00:00Z')),
          track(9, 'Newer track', new Date('2026-06-02T10:00:00Z')),
        ],
      },
      global: {
        stubs: {
          Column: ColumnStub,
          DataTable: DataTableStub,
          Dialog: DialogStub,
          InputText: InputTextStub,
          TrackShapePreview: TrackShapePreviewStub,
        },
      },
    });

    await wrapper.get('.track-id-param__open').trigger('click');
    await nextTick();

    const previews = wrapper.findAll('[data-test="shape-preview"]');
    expect(previews).toHaveLength(2);
    expect(previews.map((preview) => preview.attributes('data-track-id'))).toEqual(['9', '7']);
  });
});
