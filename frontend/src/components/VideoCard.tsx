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
      <div className="relative aspect-video bg-gradient-to-br from-[var(--accent-soft)] to-[var(--header-to)]/40">
        {video.thumbnailUrl ? (
          // Bunny's CDN thumbnail; plain <img> since it's an external, already-sized JPEG.
          // eslint-disable-next-line @next/next/no-img-element
          <img src={video.thumbnailUrl} alt="" className="h-full w-full object-cover" loading="lazy" />
        ) : (
          <div className="flex h-full items-center justify-center text-sm font-semibold text-[var(--accent-strong)] opacity-70">
            No preview yet
          </div>
        )}
        {duration && (
          <span className="absolute bottom-2 right-2 rounded-full bg-black/60 px-2 py-0.5 text-xs font-semibold text-white">{duration}</span>
        )}
        <div className="absolute left-2 top-2 flex flex-wrap gap-1.5">
          {video.assignedToMe && (
            <span className="rounded-full bg-[var(--accent)] px-2 py-0.5 text-xs font-bold text-white">Assigned to you</span>
          )}
          {video.completed && (
            <span className="rounded-full bg-[var(--success)] px-2 py-0.5 text-xs font-bold text-white">Watched</span>
          )}
        </div>
      </div>
      <div className="p-3.5">
        <p className="font-bold leading-snug">{video.title}</p>
        {STATUS_LABEL[video.status] && (
          <p className={`mt-1 text-xs ${video.status === 'FAILED' ? 'text-[var(--danger)]' : 'text-[var(--warn)]'}`}>
            {STATUS_LABEL[video.status]}
          </p>
        )}
      </div>
    </Link>
  );
}
