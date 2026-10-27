'use client';

import { useEffect, useRef } from 'react';

/** Festive colors, strong enough to show on the site's light background, with its steel blue mixed in. */
const COLORS = ['#3f74a3', '#e8a317', '#e0457b', '#2fa86e', '#8a5cd6', '#f26b38', '#1f9fbf'];
/** How long new rockets keep launching; sparks then fade out on their own. */
const LAUNCH_MS = 2600;
const GRAVITY = 0.045;

type Spark = { x: number; y: number; vx: number; vy: number; life: number; decay: number; color: string; size: number };

/**
 * A few seconds of fireworks over the whole page, drawn on a canvas that
 * ignores clicks. Calls onDone when the last spark fades.
 */
export default function Fireworks({ onDone }: { onDone?: () => void }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const doneRef = useRef(onDone);
  useEffect(() => { doneRef.current = onDone; }, [onDone]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx) return;

    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const resize = () => {
      canvas.width = window.innerWidth * dpr;
      canvas.height = window.innerHeight * dpr;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };
    resize();
    window.addEventListener('resize', resize);

    const sparks: Spark[] = [];
    const start = performance.now();
    let nextBurst = start;
    let frame = 0;

    function burst() {
      const w = window.innerWidth;
      const h = window.innerHeight;
      const x = w * (0.15 + Math.random() * 0.7);
      const y = h * (0.12 + Math.random() * 0.35);
      const color = COLORS[Math.floor(Math.random() * COLORS.length)];
      const count = 60 + Math.floor(Math.random() * 30);
      for (let i = 0; i < count; i++) {
        const angle = (Math.PI * 2 * i) / count + Math.random() * 0.2;
        const speed = 1.5 + Math.random() * 3.2;
        sparks.push({
          x, y,
          vx: Math.cos(angle) * speed,
          vy: Math.sin(angle) * speed,
          life: 1,
          decay: 0.011 + Math.random() * 0.012,
          color: Math.random() < 0.8 ? color : COLORS[Math.floor(Math.random() * COLORS.length)],
          size: 2.2 + Math.random() * 2.2,
        });
      }
    }

    function tick(now: number) {
      if (now - start < LAUNCH_MS && now >= nextBurst) {
        burst();
        nextBurst = now + 280 + Math.random() * 320;
      }
      ctx!.clearRect(0, 0, window.innerWidth, window.innerHeight);
      for (let i = sparks.length - 1; i >= 0; i--) {
        const s = sparks[i];
        s.vx *= 0.985;
        s.vy = s.vy * 0.985 + GRAVITY;
        s.x += s.vx;
        s.y += s.vy;
        s.life -= s.decay;
        if (s.life <= 0) { sparks.splice(i, 1); continue; }
        ctx!.globalAlpha = Math.min(1, s.life * 1.4);
        ctx!.fillStyle = s.color;
        ctx!.shadowColor = s.color;
        ctx!.shadowBlur = 8;
        ctx!.beginPath();
        ctx!.arc(s.x, s.y, s.size, 0, Math.PI * 2);
        ctx!.fill();
      }
      ctx!.globalAlpha = 1;
      ctx!.shadowBlur = 0;
      if (now - start < LAUNCH_MS || sparks.length > 0) frame = requestAnimationFrame(tick);
      else doneRef.current?.();
    }
    frame = requestAnimationFrame(tick);

    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener('resize', resize);
    };
  }, []);

  return <canvas ref={canvasRef} aria-hidden className="pointer-events-none fixed inset-0 z-50 h-full w-full" />;
}
