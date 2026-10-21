import type { Metadata } from 'next';
import SiteShell from './SiteShell';

// The staff area (sign-in, training videos, manager pages) stays out of search
// engines. The public home and /apply pages are left indexable.
export const metadata: Metadata = {
  title: 'Grove Salons Training',
  robots: { index: false, follow: false },
};

export default function SiteLayout({ children }: { children: React.ReactNode }) {
  return <SiteShell>{children}</SiteShell>;
}
