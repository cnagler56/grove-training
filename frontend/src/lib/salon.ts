/**
 * Salon details for the public home page. Fill these in; anything left empty
 * is simply hidden on the page.
 */
export const SALON = {
  name: 'Grove Salons',
  tagline: 'Great hair, friendly faces.',
  intro:
    'From quick trims to full color makeovers, our stylists take the time to get it right. Walk in or call ahead, whatever works for you.',

  /** e.g. "(555) 555-0100". Shown as a tap-to-call button. */
  phone: '(763) 391-6664',
  /** Street address, one line per entry. */
  address: ['9859 Maple Grove Pkwy', 'Maple Grove, MN 55369'] as string[],
  /** e.g. [["Mon–Fri", "9am–8pm"], ["Sat", "9am–6pm"], ["Sun", "10am–5pm"]] */
  hours: [
    ['Monday – Thursday', '10am – 8pm'],
    ['Friday', '10am – 6pm'],
    ['Saturday', '9am – 5pm'],
    ['Sunday', '11am – 4pm'],
  ] as [string, string][],
  /** Same hours by weekday, Sunday first, for "Open today" on the staff home page. '' = closed. */
  hoursByDay: ['11am – 4pm', '10am – 8pm', '10am – 8pm', '10am – 8pm', '10am – 8pm', '10am – 6pm', '9am – 5pm'],
  /** Same hours as 24-hour [open, close], Sunday first. The schedule editor starts new shifts from these. */
  openClose: [
    ['11:00', '16:00'], ['10:00', '20:00'], ['10:00', '20:00'], ['10:00', '20:00'],
    ['10:00', '20:00'], ['10:00', '18:00'], ['09:00', '17:00'],
  ] as [string, string][],
  /** Online booking link, if you have one. */
  bookingUrl: '',
  /** Full Instagram/Facebook links. */
  instagram: '',
  facebook: '',

  services: [
    { name: 'Haircuts', blurb: 'Classic cuts and fresh styles for women, men, and kids.' },
    { name: 'Color', blurb: 'All-over color, gray coverage, and glosses.' },
    { name: 'Highlights & balayage', blurb: 'Sun-kissed dimension, foils or hand-painted.' },
    { name: 'Blowouts & styling', blurb: 'Smooth, bouncy, or ready for a big night.' },
    { name: 'Kids’ cuts', blurb: 'Patient stylists and quick, happy cuts.' },
    { name: 'Treatments', blurb: 'Deep conditioning and scalp care.' },
  ],
};
