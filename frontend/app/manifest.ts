import type { MetadataRoute } from 'next';
import { BRAND_BLUE } from '@/src/lib/appIcon';

/**
 * Makes the site installable as a phone app ("Add to Home Screen" / "Install
 * app"). It opens full screen on the staff Home page; anyone signed out lands
 * on sign-in from there.
 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'Grove Salons',
    short_name: 'Grove Salons',
    description: 'Schedules, training videos, and your week at Grove Salons.',
    start_url: '/home',
    scope: '/',
    display: 'standalone',
    orientation: 'portrait',
    background_color: '#eef1f4',
    theme_color: BRAND_BLUE,
    icons: [
      { src: '/app-icon/192', sizes: '192x192', type: 'image/png', purpose: 'any' },
      { src: '/app-icon/512', sizes: '512x512', type: 'image/png', purpose: 'any' },
      { src: '/app-icon/512', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
    ],
  };
}
