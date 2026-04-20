import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '../views/HomeView.vue'
import LoginView from '../views/LoginView.vue'
import { isAuthenticated } from '@/utils/auth'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: LoginView
    },
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: { requiresAuth: true }
    },
    // Additive deep-link routes (C.8). All currently render the same HomeView
    // (which contains Map.vue) — Map.vue does not yet react to the route
    // params, so these are presently equivalent to '/'. When C.1 lands and
    // Map.vue is decomposed, a router-aware composable will read these
    // params to auto-open the corresponding overlay (track details, planner
    // session, statistics panel) on first render. Defining them now lets
    // browser history, sharable URLs and back/forward navigation start
    // working immediately, and avoids breaking pasted links once the
    // behavior is wired up.
    {
      path: '/track/:id(\\d+)',
      name: 'track-detail',
      component: HomeView,
      props: true,
      meta: { requiresAuth: true, deepLink: 'track' }
    },
    {
      path: '/plan/:id(\\d+)?',
      name: 'planner',
      component: HomeView,
      props: true,
      meta: { requiresAuth: true, deepLink: 'planner' }
    },
    {
      path: '/stats',
      name: 'stats',
      component: HomeView,
      meta: { requiresAuth: true, deepLink: 'stats' }
    },
    {
      path: '/about',
      name: 'about',
      // route level code-splitting
      // this generates a separate chunk (About.[hash].js) for this route
      // which is lazy-loaded when the route is visited.
      component: () => import('../views/AboutView.vue')
      // No `requiresAuth`: the About page shows licensing/source info and
      // must be reachable by any network user (AGPL-3.0 source-offer
      // obligation) even before login.
    }
  ]
})

router.beforeEach(async (to) => {
  if (to.meta.requiresAuth && !isAuthenticated()) {
    return { name: 'login' };
  } else if (to.name === 'login' && isAuthenticated()) {
    return { name: 'home' };
  }
});

export default router
