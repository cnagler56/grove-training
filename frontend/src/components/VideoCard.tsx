import Link from 'next/link';
import { formatDuration, type VideoSummary } from '@/src/lib/api';

const STATUS_LABEL: Record<VideoSummary['status'], string> = {
  UPLOADING: 'Waiting for upload',
  PROCESSING: 'Processing…',
  READY: '',
  FAILED: 'Upload failed',
};

export default function VideoCard({ video }: { video: VideoSummary }) {
  const duration = formatDuration(video.durationSeconds);
  return (
    <Link href={`/videos/${video.id}`} className="card block overflow-hidden">
      <div className="relative aspect-video bg-black/30">
        {video.thumbnailUrl ? (
          // Bunny's CDN thumbnail; plain <img> since it's an external, already-sized JPEG.
          // eslint-disable-next-line @next/next/no-img-element
          <img src={video.thumbnailUrl} alt="" className="h-full w-full object-cover" loading="lazy" />
        ) : (
          <div className="flex h-full items-center justify-center text-3xl opacity-40">▶</div>
        )}
        {duration && (
          <span className="absolute bottom-2 right-2 rounded bg-black/70 px-1.5 py-0.5 text-xs">{duration}</span>
        )}
        {video.completed && (
          <span className="absolute left-2 top-2 rounded bg-[var(--accent)] px-1.5 py-0.5 text-xs font-medium text-[var(--accent-ink)]">
            ✓ Watched
          </span>
        )}
      </div>
      <div className="p-3">
        <p className="font-medium leading-snug">{video.title}</p>
        {STATUS_LABEL[video.status] && (
          <p className={`mt-1 text-xs ${video.status === 'FAILED' ? 'text-red-300' : 'text-amber-200'}`}>
            {STATUS_LABEL[video.status]}
          </p>
        )}
      </div>
    </Link>
  );
}
