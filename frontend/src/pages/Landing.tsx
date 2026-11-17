import {
  ArrowRight,
  CalendarCheck,
  CheckCircle,
  ClockCountdown,
  Repeat,
  UsersThree,
  CaretDown,
  ShieldCheck,
} from '@phosphor-icons/react';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { cx, Tag } from '../components/ui';

const cta =
  'inline-flex items-center gap-2 min-h-12 box-border pl-6 pr-3 py-2 bg-primary text-white rounded-xl text-[18px] leading-7 font-semibold no-underline ' +
  'transition-[background-color,transform] duration-300 ease-soft hover:bg-primary-hover active:scale-[0.98]';

function Eyebrow({ children }: { children: ReactNode }) {
  return <div className="text-caption font-semibold text-primary">{children}</div>;
}

function MiniBar({
  busy,
  sel,
  thin,
}: {
  busy: [number, number][];
  sel?: [number, number];
  thin?: boolean;
}) {
  return (
    <div
      className={cx('relative rounded-full bg-track', thin ? 'h-2' : 'h-2.5')}
      aria-hidden="true"
    >
      {busy.map(([l, w], i) => (
        <div
          key={i}
          className="absolute top-0 bottom-0 rounded-full bg-busy"
          style={{ left: `${l}%`, width: `${w}%` }}
        />
      ))}
      {sel && (
        <div
          className="absolute -top-0.5 -bottom-0.5 box-border rounded border-2 border-primary"
          style={{ left: `${sel[0]}%`, width: `${sel[1]}%` }}
        />
      )}
    </div>
  );
}

function PreviewCard({
  name,
  sub,
  tags,
  busy,
  best,
}: {
  name: string;
  sub: string;
  tags: string[];
  busy: [number, number][];
  best?: boolean;
}) {
  return (
    <div
      className={cx(
        'bg-card rounded-xl p-4 flex flex-col gap-3',
        best ? 'border-2 border-primary' : 'border border-line',
      )}
    >
      <div className="flex justify-between items-start gap-2">
        <div>
          <div className="text-title font-medium">{name}</div>
          <div className="text-small text-muted">{sub}</div>
        </div>
        {best && <Tag tone="tint">Best fit</Tag>}
      </div>
      <div className="flex flex-wrap gap-1">
        {tags.map((t) => (
          <Tag key={t}>{t}</Tag>
        ))}
      </div>
      <MiniBar busy={busy} sel={[11, 11]} thin />
    </div>
  );
}

const features: { icon: ReactNode; title: string; body: string }[] = [
  {
    icon: <UsersThree size={24} aria-hidden="true" />,
    title: 'Only rooms that fit',
    body: 'Filter by date, time, headcount and equipment. A room for eight never shows up for a meeting of twelve.',
  },
  {
    icon: <ShieldCheck size={24} aria-hidden="true" />,
    title: 'No double bookings',
    body: 'Two people cannot take the same slot. The second person sees it is gone before they confirm, and gets the next free options.',
  },
  {
    icon: <Repeat size={24} aria-hidden="true" />,
    title: 'Repeating meetings that behave',
    body: 'Book every Monday for eight weeks, then change one date or the whole series without breaking the rest.',
  },
  {
    icon: <ClockCountdown size={24} aria-hidden="true" />,
    title: 'Rooms that free themselves',
    body: 'On rooms that need a check in, an empty booking is released ten minutes after the start so the room goes back to the pool.',
  },
];

const tagline =
  'Every meeting gets a room that fits. Every empty booking gets released. Nobody argues about who had it first.'.split(
    ' ',
  );

const steps = [
  {
    t: 'Search',
    b: 'Pick the date, time and number of people. Add equipment such as a projector or video call.',
  },
  {
    t: 'Choose',
    b: 'Compare the rooms that are free. Each one shows its whole day at a glance, so you can see what is around your slot.',
  },
  {
    t: 'Confirm',
    b: 'Add a title and invite your team. Everyone gets the room, the time and a reminder by email.',
  },
];

const adminPoints = [
  'Add rooms with seats and equipment in a few fields',
  'Set the open hours and the check in rule for every room',
  'See which rooms sit empty and which are always full',
];

const faqs = [
  {
    q: 'What stops two people booking the same room?',
    a: 'The system checks the time range at the moment you confirm and blocks any overlap. If someone else got there first, you are shown the next free options instead.',
  },
  {
    q: 'Can I book a meeting that repeats?',
    a: 'Yes. Choose daily, every weekday or weekly, for up to 12 meetings. Later you can change or cancel a single meeting or the rest of the series.',
  },
  {
    q: 'What happens if nobody turns up?',
    a: 'For rooms that need a check in, a booking nobody checked in to is released ten minutes after the start time, and the organizer gets an email.',
  },
  {
    q: 'How long is my slot held while I book?',
    a: 'Five minutes. If you do not confirm in that time, the slot goes back to everyone.',
  },
  {
    q: 'Do invitees get a calendar invite?',
    a: 'Yes. Everyone you invite gets an email with the room, the time and an invite file for their calendar. Changes and cancellations send an update.',
  },
  {
    q: 'Does it work on a phone?',
    a: 'Yes. The search and booking screens are laid out for small screens as well as desktops.',
  },
];

export function Landing() {
  return (
    <div className="min-h-screen bg-page text-ink">
      <header className="flex justify-center px-4 pt-4">
        <nav
          aria-label="Main"
          className="flex flex-wrap items-center justify-center gap-x-6 gap-y-2 py-2 pl-6 pr-2 bg-white/70 backdrop-blur-md border border-line rounded-3xl"
        >
          <Link to="/" className="text-[18px] leading-7 font-semibold text-primary no-underline">
            Rooms
          </Link>
          {[
            ['#features', 'Features'],
            ['#how', 'How it works'],
            ['#faq', 'FAQ'],
          ].map(([href, label]) => (
            <a
              key={href}
              href={href}
              className="flex items-center min-h-11 text-small font-medium text-body no-underline hover:text-primary"
            >
              {label}
            </a>
          ))}
          <Link
            to="/rooms/find"
            className="flex items-center min-h-11 box-border px-3 py-2 bg-primary text-white rounded-full text-small font-semibold no-underline transition-colors hover:bg-primary-hover"
          >
            Find a room
          </Link>
        </nav>
      </header>

      <main>
        <section className="flex flex-col items-center gap-8 px-6 pt-20 pb-24 text-center">
          <Tag tone="tint">Meeting room booking for offices</Tag>
          <h1 className="m-0 max-w-[680px] text-h3 md:text-h1 font-semibold text-balance pb-1 bg-gradient-to-r from-black to-[#666] bg-clip-text text-transparent">
            Find a free room
            <br />
            that fits your meeting
          </h1>
          <p className="m-0 max-w-[680px] text-title font-medium text-muted text-pretty">
            Tell Rooms the time, the headcount and what you need. You see only the rooms that are
            free and big enough, and your slot is held while you confirm.
          </p>
          <div className="flex flex-col items-center gap-3">
            <Link to="/rooms/find" className={cta}>
              Find a room <ArrowRight size={20} aria-hidden="true" />
            </Link>
            <div className="text-small text-muted">Cancel or move a booking at any time.</div>
          </div>
          <ul className="flex flex-wrap justify-center gap-x-6 gap-y-3 m-0 p-0 list-none text-small font-medium text-body">
            {[
              'Double bookings blocked',
              'Slots held for 5 minutes',
              'Empty rooms released after 10 minutes',
            ].map((t) => (
              <li key={t} className="flex items-center gap-2">
                <CheckCircle size={20} className="text-primary" aria-hidden="true" />
                {t}
              </li>
            ))}
          </ul>

          <div
            className="w-full max-w-[1040px] box-border mt-8 bg-card border border-line rounded-3xl p-2 text-left"
            aria-hidden="true"
          >
            <div className="bg-page border border-line rounded-2xl p-4 md:p-6 flex flex-col gap-6">
              <div className="flex flex-wrap gap-3">
                {[
                  ['Date', 'Wed, 18 Nov', 'flex-[1.4_1_140px]'],
                  ['From', '10:00 AM', 'flex-[1_1_100px]'],
                  ['To', '11:00 AM', 'flex-[1_1_100px]'],
                  ['People', '6', 'flex-[1_1_80px]'],
                ].map(([l, v, c]) => (
                  <div key={l} className={cx('bg-card border border-line rounded-lg px-3 py-2', c)}>
                    <div className="text-caption font-medium text-muted">{l}</div>
                    <div className="text-body font-semibold">{v}</div>
                  </div>
                ))}
                <div className="flex items-center px-6 py-2 bg-primary text-white rounded-lg text-body font-semibold">
                  Search
                </div>
              </div>
              <div className="grid gap-4 grid-cols-[repeat(auto-fit,minmax(240px,1fr))]">
                <PreviewCard
                  best
                  name="Godavari"
                  sub="6 seats on floor 3"
                  tags={['Video call', 'Whiteboard']}
                  busy={[
                    [0, 11],
                    [44, 11],
                  ]}
                />
                <PreviewCard
                  name="Ganga"
                  sub="8 seats on floor 2"
                  tags={['Video call', 'Projector']}
                  busy={[
                    [22, 17],
                    [55, 17],
                  ]}
                />
                <PreviewCard
                  name="Narmada"
                  sub="12 seats on floor 1"
                  tags={['Video call', 'Accessible']}
                  busy={[[77, 22]]}
                />
              </div>
            </div>
          </div>
        </section>

        <section id="features" className="bg-card border-y border-line px-6 py-24">
          <div className="max-w-[1040px] mx-auto flex flex-col gap-12">
            <div className="flex flex-col gap-3 max-w-[680px]">
              <Eyebrow>Features</Eyebrow>
              <h2 className="m-0 text-h3 font-semibold text-balance">
                Booking that respects how meetings really work
              </h2>
            </div>
            <div className="grid gap-6 grid-cols-[repeat(auto-fit,minmax(min(100%,380px),1fr))]">
              {features.map((f) => (
                <div
                  key={f.title}
                  className="bg-page border border-line rounded-2xl p-8 flex flex-col gap-4 transition-transform duration-500 ease-soft hover:-translate-y-1"
                >
                  <div className="size-12 flex items-center justify-center bg-tint text-primary rounded-xl">
                    {f.icon}
                  </div>
                  <h3 className="m-0 text-title font-semibold">{f.title}</h3>
                  <p className="m-0 text-body text-muted text-pretty">{f.body}</p>
                </div>
              ))}
            </div>
          </div>
        </section>

        <section className="px-6 py-24" aria-label="Why Rooms">
          <div className="max-w-[680px] mx-auto">
            <p className="m-0 text-h3 font-semibold text-balance">
              {tagline.map((w, i) => (
                <span key={i}>
                  <span
                    className="tw"
                    style={{
                      ['--a' as string]: `${10 + i * 2}%`,
                      ['--b' as string]: `${35 + i * 2}%`,
                    }}
                  >
                    {w}
                  </span>{' '}
                </span>
              ))}
            </p>
          </div>
        </section>

        <section id="how" className="bg-card border-y border-line px-6 py-24">
          <div className="max-w-[1040px] mx-auto flex flex-col gap-12">
            <div className="flex flex-col gap-3 max-w-[680px]">
              <Eyebrow>How it works</Eyebrow>
              <h2 className="m-0 text-h3 font-semibold text-balance">
                Three steps from empty calendar to confirmed room
              </h2>
            </div>
            <ol className="grid gap-8 m-0 p-0 list-none grid-cols-[repeat(auto-fit,minmax(min(100%,260px),1fr))]">
              {steps.map((s, i) => (
                <li key={s.t} className="flex flex-col gap-3">
                  <div className="size-10 flex items-center justify-center bg-primary text-white rounded-full text-body font-semibold">
                    {i + 1}
                  </div>
                  <h3 className="m-0 text-title font-semibold">{s.t}</h3>
                  <p className="m-0 text-body text-muted text-pretty">{s.b}</p>
                </li>
              ))}
            </ol>
          </div>
        </section>

        <section className="px-6 py-24">
          <div className="max-w-[1040px] mx-auto flex flex-wrap gap-12 items-center">
            <div className="flex-[1_1_360px] flex flex-col gap-3">
              <Eyebrow>For office admins</Eyebrow>
              <h2 className="m-0 text-h3 font-semibold text-balance">
                Built for the people who run the building too
              </h2>
              <p className="m-0 text-body text-muted text-pretty">
                Set the rules once and let the booking page enforce them, so nobody has to police
                the calendar.
              </p>
            </div>
            <ul className="flex-[1_1_360px] flex flex-col gap-4 m-0 p-0 list-none">
              {adminPoints.map((p) => (
                <li
                  key={p}
                  className="flex items-start gap-3 bg-card border border-line rounded-xl p-4 text-body font-medium"
                >
                  <CalendarCheck size={24} className="shrink-0 text-primary" aria-hidden="true" />
                  {p}
                </li>
              ))}
            </ul>
          </div>
        </section>

        <section id="faq" className="bg-card border-y border-line px-6 py-24">
          <div className="max-w-[760px] mx-auto flex flex-col gap-12">
            <div className="flex flex-col gap-3">
              <Eyebrow>FAQ</Eyebrow>
              <h2 className="m-0 text-h3 font-semibold text-balance">Questions people ask first</h2>
            </div>
            <div className="flex flex-col gap-3">
              {faqs.map((f) => (
                <details key={f.q} className="bg-page border border-line rounded-xl px-4">
                  <summary className="flex items-center justify-between gap-4 min-h-14 cursor-pointer text-[18px] leading-7 font-medium">
                    {f.q}
                    <CaretDown size={20} className="shrink-0 text-body" aria-hidden="true" />
                  </summary>
                  <p className="m-0 pb-4 text-body text-muted text-pretty">{f.a}</p>
                </details>
              ))}
            </div>
          </div>
        </section>

        <section className="bg-dark px-6 py-24 text-center">
          <div className="max-w-[680px] mx-auto flex flex-col items-center gap-6">
            <h2 className="m-0 text-h3 md:text-h2 font-semibold text-white text-balance">
              Stop hunting for rooms
            </h2>
            <p className="m-0 text-title font-medium text-stone-300 text-pretty">
              Find a free room that fits your meeting in a few clicks. Cancel or move it whenever
              plans change.
            </p>
            <Link to="/rooms/find" className={cta}>
              Find a room <ArrowRight size={20} aria-hidden="true" />
            </Link>
          </div>
        </section>
      </main>

      <footer className="bg-dark border-t border-[#313131] px-6 py-8">
        <div className="max-w-[1040px] mx-auto flex flex-wrap items-center justify-between gap-4">
          <div className="text-small font-medium text-stone-300">Rooms, meeting room booking</div>
          <Link
            to="/rooms/find"
            className="flex items-center min-h-11 text-small font-medium text-stone-300 no-underline hover:text-white"
          >
            Find a room
          </Link>
        </div>
      </footer>
    </div>
  );
}
