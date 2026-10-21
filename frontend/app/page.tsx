import Link from 'next/link';
import PublicNav from '@/src/components/PublicNav';
import { SALON } from '@/src/lib/salon';

/** Public home page for customers and visitors. Edit the details in src/lib/salon.ts. */
export default function HomePage() {
  const hasVisitInfo = SALON.address.length > 0 || SALON.hours.length > 0 || SALON.phone;

  return (
    <>
      <PublicNav />

      {/* Hero */}
      <header className="bg-gradient-to-br from-[var(--header-from)] to-[var(--header-to)] text-white">
        <div className="mx-auto max-w-5xl px-4 py-16 sm:py-24">
          <p className="text-sm font-bold uppercase tracking-widest opacity-90">{SALON.name}</p>
          <h1 className="mt-2 max-w-2xl text-4xl font-extrabold leading-tight tracking-tight sm:text-6xl">
            {SALON.tagline}
          </h1>
          <p className="mt-4 max-w-xl text-lg opacity-95">{SALON.intro}</p>
          <div className="mt-8 flex flex-wrap gap-3">
            {SALON.bookingUrl && (
              <a href={SALON.bookingUrl} target="_blank" rel="noreferrer noopener"
                className="rounded-full bg-white px-6 py-3 font-bold text-[var(--accent-strong)] shadow hover:bg-white/90">
                Book an appointment
              </a>
            )}
            {SALON.phone && (
              <a href={`tel:${SALON.phone}`}
                className="rounded-full bg-white/20 px-6 py-3 font-bold ring-1 ring-white/50 hover:bg-white/30">
                Call {SALON.phone}
              </a>
            )}
            <Link href="#services"
              className="rounded-full bg-white/20 px-6 py-3 font-bold ring-1 ring-white/50 hover:bg-white/30">
              See our services
            </Link>
          </div>
        </div>
      </header>

      <main>
        {/* Services */}
        <section id="services" className="mx-auto max-w-5xl scroll-mt-20 px-4 py-16">
          <h2 className="text-3xl font-extrabold tracking-tight">What we do</h2>
          <p className="mt-2 opacity-70">Ask us anything. We&apos;re happy to talk through what you have in mind.</p>
          <div className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {SALON.services.map((s) => (
              <div key={s.name} className="card p-5">
                <h3 className="text-lg font-extrabold">{s.name}</h3>
                <p className="mt-1 text-sm opacity-75">{s.blurb}</p>
              </div>
            ))}
          </div>
        </section>

        {/* Visit */}
        <section id="visit" className="scroll-mt-20 bg-[var(--surface)]">
          <div className="mx-auto grid max-w-5xl gap-8 px-4 py-16 md:grid-cols-2">
            <div>
              <h2 className="text-3xl font-extrabold tracking-tight">Come see us</h2>
              <p className="mt-2 opacity-75">
                Walk-ins are always welcome. Call ahead if you&apos;d like to check wait times.
              </p>
              {SALON.address.length > 0 && (
                <address className="mt-6 not-italic">
                  <div className="text-xs font-bold uppercase tracking-wide opacity-60">Address</div>
                  {SALON.address.map((line) => <div key={line} className="font-semibold">{line}</div>)}
                  <a
                    href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(SALON.address.join(', '))}`}
                    target="_blank" rel="noreferrer noopener"
                    className="mt-1 inline-block text-sm font-semibold text-[var(--accent-strong)] underline underline-offset-4"
                  >
                    Get directions
                  </a>
                </address>
              )}
              {SALON.phone && (
                <div className="mt-4">
                  <div className="text-xs font-bold uppercase tracking-wide opacity-60">Phone</div>
                  <a href={`tel:${SALON.phone}`} className="font-semibold hover:underline">{SALON.phone}</a>
                </div>
              )}
              {(SALON.instagram || SALON.facebook) && (
                <div className="mt-4 flex gap-3 text-sm font-semibold">
                  {SALON.instagram && <a href={SALON.instagram} target="_blank" rel="noreferrer noopener" className="text-[var(--accent-strong)] hover:underline">Instagram</a>}
                  {SALON.facebook && <a href={SALON.facebook} target="_blank" rel="noreferrer noopener" className="text-[var(--accent-strong)] hover:underline">Facebook</a>}
                </div>
              )}
            </div>
            {SALON.hours.length > 0 ? (
              <div className="card p-6">
                <h3 className="text-lg font-extrabold">Hours</h3>
                <dl className="mt-3 divide-y divide-[var(--border)] text-sm">
                  {SALON.hours.map(([days, time]) => (
                    <div key={days} className="flex justify-between py-2">
                      <dt className="font-semibold">{days}</dt>
                      <dd className="opacity-80">{time}</dd>
                    </div>
                  ))}
                </dl>
              </div>
            ) : !hasVisitInfo ? (
              <div className="card flex items-center justify-center p-6 text-center opacity-70">
                Stop by anytime. We&apos;d love to meet you.
              </div>
            ) : null}
          </div>
        </section>

        {/* Careers */}
        <section className="mx-auto max-w-5xl px-4 py-16">
          <div className="flex flex-wrap items-center justify-between gap-6 rounded-3xl bg-[var(--accent-soft)] p-8">
            <div className="max-w-xl">
              <h2 className="text-2xl font-extrabold tracking-tight">Love doing hair? Join our team.</h2>
              <p className="mt-2 opacity-80">
                We&apos;re hiring stylists and front-desk team members. Ongoing training included.
              </p>
            </div>
            <Link href="/apply" className="btn">Apply now</Link>
          </div>
        </section>
      </main>

      <footer className="border-t border-[var(--border)]">
        <div className="mx-auto flex max-w-5xl flex-wrap items-center justify-between gap-3 px-4 py-6 text-sm opacity-70">
          <span>© {new Date().getFullYear()} {SALON.name}</span>
          <Link href="/signin" className="hover:underline">Staff sign in</Link>
        </div>
      </footer>
    </>
  );
}
