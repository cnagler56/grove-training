import * as tus from 'tus-js-client';
import type { UploadTicket } from './api';

/**
 * Upload a file straight from the browser to Bunny Stream over TUS using a
 * pre-signed ticket from our backend. TUS is resumable: a dropped salon Wi-Fi
 * connection retries on its own, and re-picking the same file on the same
 * video picks up where it left off.
 */
export function uploadToBunny(
  file: File,
  ticket: UploadTicket,
  title: string,
  onProgress: (fraction: number) => void,
): { promise: Promise<void>; abort: () => void } {
  let upload: tus.Upload;
  const promise = new Promise<void>((resolve, reject) => {
    upload = new tus.Upload(file, {
      endpoint: ticket.endpoint,
      retryDelays: [0, 3000, 5000, 10000, 20000, 60000],
      chunkSize: 50 * 1024 * 1024,
      headers: {
        AuthorizationSignature: ticket.signature,
        AuthorizationExpire: String(ticket.expires),
        VideoId: ticket.bunnyVideoId,
        LibraryId: ticket.libraryId,
      },
      metadata: { filetype: file.type, title },
      onProgress: (sent, total) => onProgress(total ? sent / total : 0),
      onError: (err) => reject(err),
      onSuccess: () => resolve(),
    });
    upload.findPreviousUploads().then((previous) => {
      if (previous.length) upload.resumeFromPreviousUpload(previous[0]);
      upload.start();
    });
  });
  return { promise, abort: () => void upload?.abort() };
}
