// Used by .github/workflows/build-apk.yml to connect the app with Firebase App Distribution.
//
//   node scripts/app-distribution.mjs pending            -> prints emails phones registered
//                                                           (Firestore tester_requests, status "pending")
//   node scripts/app-distribution.mjs done testers.txt   -> marks those emails "added" and
//                                                           publishes app_release/latest so
//                                                           the app shows "New version available"
//
// Auth: GOOGLE_APPLICATION_CREDENTIALS must point at the Firebase service-account JSON.
import { readFileSync } from 'node:fs';
import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';

initializeApp({ credential: applicationDefault() });
const db = getFirestore();
const [mode, file] = process.argv.slice(2);

if (mode === 'pending') {
  const snap = await db.collection('tester_requests').where('status', '==', 'pending').get();
  const emails = snap.docs.map(d => (d.get('email') || d.id).trim().toLowerCase()).filter(e => e.includes('@'));
  process.stdout.write(emails.join('\n') + (emails.length ? '\n' : ''));
} else if (mode === 'done') {
  const emails = file ? readFileSync(file, 'utf8').split('\n').map(s => s.trim()).filter(Boolean) : [];
  const batch = db.batch();
  for (const e of emails) {
    batch.set(db.collection('tester_requests').doc(e), { status: 'added', addedAt: FieldValue.serverTimestamp() }, { merge: true });
  }
  const versionCode = parseInt(process.env.VERSION_CODE || '0', 10);
  batch.set(db.collection('app_release').doc('latest'), {
    versionCode,
    versionName: `1.0.${versionCode}`,
    notes: (process.env.RELEASE_NOTES || '').slice(0, 500),
    url: 'https://appdistribution.firebase.google.com/testerapps',
    updatedAt: FieldValue.serverTimestamp(),
  });
  await batch.commit();
  console.log(`Marked ${emails.length} tester(s) added; app_release/latest = ${versionCode}`);
} else {
  console.error('usage: app-distribution.mjs pending | done <file>');
  process.exit(1);
}
