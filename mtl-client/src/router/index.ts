import { createRouter, createWebHistory } from 'vue-router';
import HomeView from '../views/HomeView.vue';
import LoginView from '../views/LoginView.vue';
import { isAuthenticated } from '@/utils/auth';

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView,
    },
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: { requiresAuth: true },
    },
    {
      path: '/track/:id(\\d+)',
      name: 'track-detail',
      component: HomeView,
      props: true,
      meta: { requiresAuth: true, deepLink: 'track' },
    },
    {
      path: '/plan/:id(\\d+)?',
      name: 'planner',
      component: HomeView,
      props: true,
      meta: { requiresAuth: true, deepLink: 'planner' },
    },
    {
      path: '/stats',
      name: 'stats',
      component: HomeView,
      meta: { requiresAuth: true, deepLink: 'stats' },
    },
    {
      path: '/filter',
      name: 'filter',
      component: HomeView,
      meta: { requiresAuth: true, deepLink: 'filter' },
    },
    {
      path: '/map-settings',
      name: 'map-settings',
      component: HomeView,
      meta: { requiresAuth: true, deepLink: 'map' },
    },
    {
      path: '/animate',
      name: 'animate',
      component: HomeView,
      meta: { requiresAuth: true, deepLink: 'animate' },
    },
    {
      path: '/segments',
      name: 'segments',
      component: HomeView,
      meta: { requiresAuth: true, deepLink: 'measure' },
    },
    {
      path: '/admin/:section?',
      name: 'admin',
      component: HomeView,
      meta: { requiresAuth: true, deepLink: 'admin' },
    },
    {
      path: '/about',
      name: 'about',
      // route level code-splitting
      // this generates a separate chunk (About.[hash].js) for this route
      // which is lazy-loaded when the route is visited.
      component: () => import('../views/AboutView.vue'),
      // No `requiresAuth`: the About page shows licensing/source info and
      // must be reachable by any network user (AGPL-3.0 source-offer
      // obligation) even before login.
    },
  ],
});

router.beforeEach(async (to) => {
  if (to.meta.requiresAuth && !isAuthenticated()) {
    return { name: 'login' };
  } else if (to.name === 'login' && isAuthenticated()) {
    return { name: 'home' };
  }
});

export default router;
