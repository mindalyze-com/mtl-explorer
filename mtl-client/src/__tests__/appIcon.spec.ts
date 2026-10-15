import { mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import AppIcon from '@/components/ui/AppIcon.vue';

describe('AppIcon', () => {
  it.each(['bi bi-gear', 'pi pi-cog'])('renders %s as decorative by default', (icon) => {
    const wrapper = mount(AppIcon, { props: { icon } });
    expect(wrapper.classes()).toEqual(expect.arrayContaining(icon.split(' ')));
    expect(wrapper.attributes('aria-hidden')).toBe('true');
    expect(wrapper.find('.app-icon__dot').exists()).toBe(false);
    wrapper.unmount();
  });

  it('supports class bindings while preserving presentation attributes and events', async () => {
    const onClick = vi.fn();
    const wrapper = mount(AppIcon, {
      props: { icon: ['bi', { 'bi-gear': true, 'bi-map': false }] },
      attrs: { class: 'toolbar-icon', title: 'Settings', style: 'font-size: 24px', onClick },
    });
    expect(wrapper.classes()).toEqual(expect.arrayContaining(['toolbar-icon', 'bi-gear']));
    expect(wrapper.classes()).not.toContain('bi-map');
    expect(wrapper.attributes('title')).toBe('Settings');
    expect((wrapper.element as HTMLElement).style.fontSize).toBe('24px');
    await wrapper.trigger('click');
    expect(onClick).toHaveBeenCalledOnce();
    wrapper.unmount();
  });

  it('exposes a meaningful icon through its label and supports removing that label', async () => {
    const wrapper = mount(AppIcon, { props: { icon: 'bi bi-info-circle', label: 'Information' } });
    expect(wrapper.attributes('role')).toBe('img');
    expect(wrapper.attributes('aria-label')).toBe('Information');
    expect(wrapper.attributes('aria-hidden')).toBeUndefined();
    await wrapper.setProps({ label: undefined });
    expect(wrapper.attributes('aria-hidden')).toBe('true');
    expect(wrapper.attributes('aria-label')).toBeUndefined();
    wrapper.unmount();
  });

  it('changes status and glyph together and restores the base icon when status clears', async () => {
    const wrapper = mount(AppIcon, {
      props: {
        icon: 'bi bi-funnel',
        statusDot: { tone: 'notice', pulse: true, icon: 'bi bi-funnel-fill' },
      },
    });
    expect(wrapper.classes()).toContain('bi-funnel-fill');
    expect(wrapper.classes()).not.toContain('bi-funnel');
    expect(wrapper.get('.app-icon__dot').classes()).toContain('app-icon__dot--pulse');
    await wrapper.setProps({ statusDot: { tone: 'warning' } });
    expect(wrapper.get('.app-icon__dot').classes()).toContain('app-icon__dot--warning');
    expect(wrapper.get('.app-icon__dot').classes()).not.toContain('app-icon__dot--pulse');
    await wrapper.setProps({ statusDot: false });
    expect(wrapper.find('.app-icon__dot').exists()).toBe(false);
    expect(wrapper.classes()).toContain('bi-funnel');
    wrapper.unmount();
  });

  it('supports a simple steady status dot without announcing it separately', () => {
    const wrapper = mount(AppIcon, {
      props: { icon: 'bi bi-gear', statusDot: true },
    });
    expect(wrapper.get('.app-icon__dot').attributes('aria-hidden')).toBe('true');
    expect(wrapper.get('.app-icon__dot').classes()).toContain('app-icon__dot--notice');
    expect(wrapper.get('.app-icon__dot').classes()).not.toContain('app-icon__dot--pulse');
    wrapper.unmount();
  });
});
