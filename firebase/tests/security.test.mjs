import { after, before, beforeEach, test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { createHash, randomUUID } from 'node:crypto';
import { initializeApp, deleteApp } from 'firebase/app';
import { getAuth, connectAuthEmulator, createUserWithEmailAndPassword, sendEmailVerification,
  applyActionCode, reload, signOut, signInWithEmailAndPassword, sendPasswordResetEmail,
  confirmPasswordReset, EmailAuthProvider, reauthenticateWithCredential, updatePassword,
  verifyBeforeUpdateEmail, deleteUser } from 'firebase/auth';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, collection, getDoc, getDocs, query, limit, setDoc, deleteDoc, writeBatch,
  serverTimestamp, Timestamp, runTransaction } from 'firebase/firestore';
import { ref, uploadBytes, getBytes, list, deleteObject } from 'firebase/storage';

// This demo project and synthetic identities are used only by local emulators. No production
// credentials, private account tokens, service accounts, or admin endpoints are part of this suite.
const projectId = 'demo-veyra';
let env;
const hash = value => createHash('sha256').update(value).digest('hex');
const client = uid => env.authenticatedContext(uid, { email: `${uid}@example.test`, email_verified: true });
const db = uid => client(uid).firestore();
const root = (database, uid) => doc(database, `users/${uid}`);
const path = (uid, bucket, id) => `users/${uid}/${bucket}/${hash(id)}`;

function envelope(uid, id = 'coffee', type = 'expense', overrides = {}) {
  return {
    id, ownerUid: uid, type, title: 'Café', notes: '', date: '2026-10-06', done: false,
    favorite: false, tags: '', parentId: '', fields: { amountMinor: '800', amount: '8.00', status: 'paid', currency: 'BRL' },
    amountMinor: 800, currency: 'BRL', category: '', accountId: '', accountDocId: '',
    destinationId: '', destinationDocId: '', cardId: '', cardDocId: '', status: 'paid',
    recurrenceId: '', createdAt: 1234, deletedAt: 0, revision: 1, operationId: `create-${id}`,
    updatedAt: serverTimestamp(), ...overrides,
  };
}
async function write(uid, bucket, item, database = db(uid)) {
  const batch = writeBatch(database);
  batch.set(doc(database, path(uid, bucket, item.id)), item);
  batch.update(root(database, uid), { latestChangeAt: serverTimestamp(), updatedAt: serverTimestamp() });
  return batch.commit();
}
async function seedProfile(uid) {
  await env.withSecurityRulesDisabled(async context => {
    await setDoc(root(context.firestore(), uid), {
      ownerUid: uid, name: uid, email: `${uid}@example.test`, photo: '', emailVerified: true,
      createdAt: Timestamp.fromMillis(1), updatedAt: Timestamp.fromMillis(1),
      latestChangeAt: Timestamp.fromMillis(1), lastAccessAt: Timestamp.fromMillis(1), deleting: false,
    });
  });
}
before(async () => {
  env = await initializeTestEnvironment({ projectId,
    firestore: { host: '127.0.0.1', port: 8085, rules: await readFile(new URL('../firestore.rules', import.meta.url), 'utf8') },
    storage: { host: '127.0.0.1', port: 9195, rules: await readFile(new URL('../storage.rules', import.meta.url), 'utf8') },
  });
});
beforeEach(async () => { await env.clearFirestore(); await env.clearStorage(); await seedProfile('alice'); await seedProfile('bob'); });
after(async () => { await env?.cleanup(); });

test('real profile creation uses the owner path and server timestamps with no additional privilege fields', async () => {
  const uid = 'new-profile';
  const profile = { ownerUid: uid, name: 'Nova conta', email: 'new-profile@example.test', photo: '', emailVerified: true,
    createdAt: serverTimestamp(), updatedAt: serverTimestamp(), latestChangeAt: serverTimestamp(), lastAccessAt: serverTimestamp(), deleting: false };
  await assertSucceeds(setDoc(root(db(uid), uid), profile));
  await assertFails(setDoc(root(db('alice'), 'alice'), { ...profile, ownerUid: 'alice', admin: true }));
  await assertFails(setDoc(root(db('alice'), 'another-owner'), { ...profile, ownerUid: 'another-owner' }));
});
test('verified owner can create, read, revise, and soft delete its transaction', async () => {
  await assertSucceeds(write('alice', 'transactions', envelope('alice')));
  const database = db('alice');
  assert.equal((await assertSucceeds(getDoc(doc(database, path('alice', 'transactions', 'coffee'))))).data().amountMinor, 800);
  await assertSucceeds(write('alice', 'transactions', envelope('alice', 'coffee', 'expense', { revision: 2, operationId: 'edit-coffee', title: 'Café corrigido' })));
  await assertSucceeds(write('alice', 'transactions', envelope('alice', 'coffee', 'expense', { revision: 3, operationId: 'trash-coffee', deletedAt: 123456 })));
});
test('user A cannot read, write, query, or delete user B private documents', async () => {
  await write('bob', 'transactions', envelope('bob'));
  const alice = db('alice');
  await assertFails(getDoc(doc(alice, path('bob', 'transactions', 'coffee'))));
  await assertFails(getDocs(query(collection(alice, 'users/bob/transactions'), limit(100))));
  await assertFails(setDoc(doc(alice, path('bob', 'transactions', 'new')), envelope('bob', 'new')));
  await assertFails(deleteDoc(doc(alice, path('bob', 'transactions', 'coffee'))));
});
test('anonymous and unverified accounts cannot access financial records', async () => {
  await write('alice', 'transactions', envelope('alice'));
  await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), path('alice', 'transactions', 'coffee'))));
  const unverified = env.authenticatedContext('alice', { email: 'alice@example.test', email_verified: false }).firestore();
  await assertFails(getDoc(doc(unverified, path('alice', 'transactions', 'coffee'))));
  await assertFails(write('alice', 'transactions', envelope('alice', 'unverified'), unverified));
});
test('owner cannot forge ownership, collection routing, unexpected admin fields, or raw attachments', async () => {
  await assertFails(write('alice', 'transactions', envelope('alice', 'owner', 'expense', { ownerUid: 'bob' })));
  await assertFails(write('alice', 'workspace', envelope('alice', 'bad-bucket')));
  await assertFails(write('alice', 'transactions', envelope('alice', 'admin', 'expense', { admin: true })));
  await assertFails(write('alice', 'transactions', envelope('alice', 'binary', 'expense', { fields: { attachment: 'base64', amountMinor: '800', status: 'paid', currency: 'BRL' } })));
});
test('money must be a bounded integer and agree with the persistent minor-unit field', async () => {
  for (const [id, amountMinor] of [['fraction', 8.25], ['text', '800'], ['negative', -800], ['huge', 900000000000001]])
    await assertFails(write('alice', 'transactions', envelope('alice', id, 'expense', { amountMinor })));
  await assertFails(write('alice', 'transactions', envelope('alice', 'mismatch', 'expense', { fields: { amountMinor: '801', status: 'paid', currency: 'BRL' } })));
});
test('calendar validation accepts leap day and rejects invalid days/months', async () => {
  await assertSucceeds(write('alice', 'transactions', envelope('alice', 'leap', 'expense', { date: '2028-02-29' })));
  for (const date of ['2026-02-29', '2026-04-31', '2026-13-01', '2026-01-00', '0000-01-01', '2026-1-01', 'invalid'])
    await assertFails(write('alice', 'transactions', envelope('alice', date, 'expense', { date })));
});
test('due, settlement and recurrence dates are real calendar dates and recurrence limits stay bounded', async () => {
  const recurrence = envelope('alice', 'monthly', 'recurring_rule', { fields: {
    amountMinor: '800', startDate: '2028-02-29', endDate: '2030-12-31', pausedAt: '2029-01-01', interval: '1',
    dayOfMonth: '31', occurrenceCount: '24', transactionType: 'expense', customUnit: 'months', frequency: 'MONTHLY',
  }, status: '' });
  await assertSucceeds(write('alice', 'recurringTransactions', recurrence));
  const payable = envelope('alice', 'due-date', 'expense', { fields: {
    amountMinor: '800', status: 'paid', currency: 'BRL', dueDate: '2028-02-29', settledDate: '2028-03-01',
  } });
  await assertSucceeds(write('alice', 'transactions', payable));
  for (const [key, value] of [['dueDate', '2026-02-29'], ['settledDate', '2026-04-31']])
    await assertFails(write('alice', 'transactions', { ...payable, id: key, fields: { ...payable.fields, [key]: value } }));
  for (const [key, value] of [['interval', '0'], ['interval', '366'], ['dayOfMonth', '32'], ['occurrenceCount', '10001'],
    ['transactionType', 'transfer'], ['customUnit', 'minutes'], ['frequency', 'every-second'], ['endDate', '2027-01-01'], ['pausedAt', '2026-02-30']])
    await assertFails(write('alice', 'recurringTransactions', { ...recurrence, id: `${key}-${value}`, fields: { ...recurrence.fields, [key]: value } }));
});
test('subscription and legacy recurrence labels accept optional blanks without relaxing recurrence limits', async () => {
  const fields = { amountMinor: '800', startDate: '2028-02-29', endDate: '', pausedAt: '', frequency: 'mEnSaL',
    interval: '', dayOfMonth: '', occurrenceCount: '', transactionType: '', customUnit: '' };
  await assertSucceeds(write('alice', 'subscriptions', envelope('alice', 'service', 'subscription', { fields, status: '' })));
  await assertSucceeds(write('alice', 'recurringTransactions', envelope('alice', 'legacy', 'recurring_rule', { fields, status: '' })));
  for (const [key, value] of [['frequency', 'unknown'], ['interval', '999'], ['occurrenceCount', '10001']])
    await assertFails(write('alice', 'subscriptions', envelope('alice', `${key}-${value}`, 'subscription', { fields: { ...fields, [key]: value }, status: '' })));
});
test('credit-card calendar days and goal/installment dates cannot be invalid', async () => {
  const card = envelope('alice', 'card', 'card', { fields: { limitMinor: '100000', closing: '31', due: '10' }, amountMinor: 0, status: '' });
  await assertSucceeds(write('alice', 'creditCards', card));
  for (const [key, value] of [['closing', '0'], ['closing', '32'], ['due', '-1'], ['due', '40']])
    await assertFails(write('alice', 'creditCards', { ...card, id: `${key}-${value}`, fields: { ...card.fields, [key]: value } }));
  const goal = envelope('alice', 'target', 'savings_goal', { fields: { amountMinor: '800', targetDate: '2028-02-29' }, status: '' });
  await assertSucceeds(write('alice', 'goals', goal));
  await assertFails(write('alice', 'goals', { ...goal, id: 'bad-target', fields: { ...goal.fields, targetDate: '2026-02-29' } }));
  const installment = envelope('alice', 'installment', 'expense', { fields: {
    amountMinor: '800', currency: 'BRL', status: 'paid', firstInstallment: '2028-02-29', installmentAmountMinor: '200',
  } });
  await assertSucceeds(write('alice', 'transactions', installment));
  await assertFails(write('alice', 'transactions', { ...installment, id: 'bad-installment-date', fields: { ...installment.fields, firstInstallment: '2026-04-31' } }));
});
test('asset and historical snapshots use their financial collection and bounded signed minor units', async () => {
  for (const type of ['financial_asset', 'card_carry', 'networth_snapshot', 'month_close']) {
    const item = envelope('alice', type, type, { amountMinor: 0, status: '', fields: {
      currency: 'BRL', currentMinor: '10000', netWorthMinor: '-20000', savingsMinor: '-300',
      incomeMinor: '1000', expenseMinor: '1300',
    } });
    await assertSucceeds(write('alice', 'assets', item));
    await assertFails(write('alice', 'workspace', { ...item, id: `wrong-${type}` }));
  }
  for (const [key, value] of [['currentMinor', '-1'], ['netWorthMinor', '900000000000001'],
    ['savingsMinor', '0.25'], ['incomeMinor', '-100'], ['expenseMinor', 'invalid']])
    await assertFails(write('alice', 'assets', envelope('alice', `invalid-${key}`, 'financial_asset', {
      amountMinor: 0, status: '', fields: { [key]: value },
    })));
});
test('contribution and installment minor units cannot be negative, fractional or outside the safe range', async () => {
  const goal = envelope('alice', 'reserve', 'savings_goal', { amountMinor: 10000, status: '',
    fields: { amountMinor: '10000', currentMinor: '5000', monthlyContributionMinor: '1000' } });
  const plan = envelope('alice', 'plan', 'installment_plan', { amountMinor: 10000, status: '',
    fields: { amountMinor: '10000', installmentAmountMinor: '2500' } });
  await assertSucceeds(write('alice', 'goals', goal));
  await assertSucceeds(write('alice', 'recurringTransactions', plan));
  for (const [bucket, item, key] of [['goals', goal, 'monthlyContributionMinor'], ['recurringTransactions', plan, 'installmentAmountMinor']])
    for (const value of ['-1', '0.25', '900000000000001'])
      await assertFails(write('alice', bucket, { ...item, id: `${key}-${value}`, fields: { ...item.fields, [key]: value } }));
});
test('category type and budget warning thresholds keep their domain constraints', async () => {
  const category = envelope('alice', 'food', 'financial_category', { status: '', amountMinor: 0, fields: { kind: 'expense', parent: 'living' } });
  await assertSucceeds(write('alice', 'categories', category));
  await assertSucceeds(write('alice', 'categories', { ...category, id: 'shared-category', fields: { kind: '', transactionType: '' } }));
  await assertFails(write('alice', 'categories', { ...category, id: 'bad-kind', fields: { kind: 'transfer' } }));
  const budget = envelope('alice', 'food-budget', 'budget', { status: '', fields: { amountMinor: '800', thresholds: '50,75,90,100' } });
  await assertSucceeds(write('alice', 'budgets', budget));
  await assertSucceeds(write('alice', 'budgets', { ...budget, id: 'default-thresholds', fields: { ...budget.fields, thresholds: '' } }));
  for (const thresholds of ['0,100', '50,101', '50,50,100', '-1', '0.5', 'text'])
    await assertFails(write('alice', 'budgets', { ...budget, id: `bad-${thresholds}`, fields: { ...budget.fields, thresholds } }));
});
test('client clock cannot replace server timestamp or omit the atomic change marker', async () => {
  await assertFails(write('alice', 'transactions', envelope('alice', 'clock', 'expense', { updatedAt: Timestamp.fromMillis(1) })));
  await assertFails(setDoc(doc(db('alice'), path('alice', 'transactions', 'unmarked')), envelope('alice', 'unmarked')));
});
test('stale revisions, ID/type replacement and repeated operation IDs are rejected', async () => {
  await write('alice', 'transactions', envelope('alice'));
  await assertFails(write('alice', 'transactions', envelope('alice', 'coffee', 'expense', { operationId: 'stale' })));
  await assertFails(write('alice', 'transactions', envelope('alice', 'coffee', 'income', { revision: 2, operationId: 'change-type' })));
  await assertFails(write('alice', 'transactions', envelope('alice', 'coffee', 'expense', { revision: 2 })));
});
test('optimistic transaction preserves a competing revision for explicit conflict resolution', async () => {
  await write('alice', 'transactions', envelope('alice'));
  const deviceOne = db('alice');
  const deviceTwo = db('alice');
  const reference = doc(deviceOne, path('alice', 'transactions', 'coffee'));
  const expected = (await getDoc(reference)).data().revision;
  await write('alice', 'transactions', envelope('alice', 'coffee', 'expense', { revision: 2, operationId: 'device-two', title: 'Outro dispositivo' }), deviceTwo);
  const result = await runTransaction(deviceOne, async transaction => {
    const current = await transaction.get(reference);
    if (current.data().revision !== expected) return { conflict: true, remote: current.data().title };
    throw new Error('A competing revision must not be overwritten.');
  });
  assert.deepEqual(result, { conflict: true, remote: 'Outro dispositivo' });
});
test('physical document removal is only permitted after account deletion lock', async () => {
  await write('alice', 'transactions', envelope('alice'));
  const database = db('alice');
  await assertFails(deleteDoc(doc(database, path('alice', 'transactions', 'coffee'))));
  await assertSucceeds(setDoc(root(database, 'alice'), { deleting: true, updatedAt: serverTimestamp() }, { merge: true }));
  await assertSucceeds(deleteDoc(doc(database, path('alice', 'transactions', 'coffee'))));
  await assertFails(write('alice', 'transactions', envelope('alice', 'resurrect')));
  await assertFails(setDoc(root(database, 'alice'), { deleting: false, updatedAt: serverTimestamp() }, { merge: true }));
});
test('permanent purge preserves tombstone while removing monetary payload', async () => {
  await write('alice', 'transactions', envelope('alice'));
  await assertSucceeds(write('alice', 'transactions', envelope('alice', 'coffee', 'expense', { revision: 2, operationId: 'purge', deletedAt: 123456, fields: {}, amountMinor: 0, status: '' })));
});
test('collection queries require a bounded page', async () => {
  await assertSucceeds(getDocs(query(collection(db('alice'), 'users/alice/transactions'), limit(100))));
  await assertFails(getDocs(collection(db('alice'), 'users/alice/transactions')));
  await assertFails(getDocs(query(collection(db('alice'), 'users/alice/transactions'), limit(1000))));
});
test('transfer references only the current owner accounts and moves one atomic record', async () => {
  const account = (uid, id) => envelope(uid, id, 'account', { fields: { currency: 'BRL' }, status: '', amountMinor: 0 });
  await write('alice', 'accounts', account('alice', 'bank'));
  await write('alice', 'accounts', account('alice', 'wallet'));
  await write('bob', 'accounts', account('bob', 'foreign'));
  const fields = { amountMinor: '10000', status: 'paid', currency: 'BRL', account: 'bank', destination: 'wallet' };
  const transfer = envelope('alice', 'transfer', 'transfer', { fields, amountMinor: 10000, accountId: 'bank', accountDocId: hash('bank'), destinationId: 'wallet', destinationDocId: hash('wallet') });
  await assertSucceeds(write('alice', 'transactions', transfer));
  await assertFails(write('alice', 'transactions', { ...transfer, id: 'foreign-transfer', operationId: 'foreign', destinationId: 'foreign', destinationDocId: hash('foreign'), fields: { ...fields, destination: 'foreign' } }));
  await assertFails(write('alice', 'transactions', { ...transfer, id: 'self-transfer', operationId: 'self', destinationId: 'bank', destinationDocId: hash('bank'), fields: { ...fields, destination: 'bank' } }));
  await write('alice', 'accounts', { ...account('alice', 'wallet'), revision: 2, operationId: 'delete-wallet', deletedAt: 123456 });
  await assertFails(write('alice', 'transactions', { ...transfer, id: 'deleted-account', operationId: 'deleted-account' }));
});
test('complete dated card expense can be created and revised using active owner account/card references', async () => {
  await write('alice', 'accounts', envelope('alice', 'bank', 'account', { amountMinor: 0, status: '', fields: { currency: 'BRL', openingMinor: '0' } }));
  await write('alice', 'creditCards', envelope('alice', 'credit', 'card', { amountMinor: 0, status: '', fields: { currency: 'BRL', limitMinor: '100000', closing: '31', due: '10' } }));
  await write('bob', 'creditCards', envelope('bob', 'foreign-card', 'card', { amountMinor: 0, status: '', fields: { currency: 'BRL', limitMinor: '100000' } }));
  const item = envelope('alice', 'rich-expense', 'expense', { date: '2028-02-29', category: 'Alimentação',
    accountId: 'bank', accountDocId: hash('bank'), cardId: 'credit', cardDocId: hash('credit'), fields: {
      amountMinor: '800', currency: 'BRL', status: 'paid', category: 'Alimentação', account: 'bank', card: 'credit',
      dueDate: '2028-03-31', settledDate: '2028-03-15', firstInstallment: '2028-03-15', installmentAmountMinor: '200',
    } });
  await assertSucceeds(write('alice', 'transactions', item));
  await assertSucceeds(write('alice', 'transactions', { ...item, revision: 2, operationId: 'rich-edit', notes: 'Edição completa' }));
  await assertFails(write('alice', 'transactions', { ...item, id: 'foreign-card-expense', cardId: 'foreign-card', cardDocId: hash('foreign-card'), fields: { ...item.fields, card: 'foreign-card' } }));
});
test('synced settings cannot contain lock, passwords, tokens, assistant credentials or consent from another device', async () => {
  const settings = envelope('alice', 'workspace-settings', 'cloud_settings', { date: '', fields: { name: 'Alice', theme: 'Escuro' }, amountMinor: 0, status: '' });
  await assertSucceeds(write('alice', 'settings', settings));
  for (const key of ['lock', 'financeLock', 'password', 'token', 'onlineKey', 'aiEndpoint', 'weatherConsent',
    'financeNotificationValues', 'widgetFinance', 'financeWidgetValues'])
    await assertFails(write('alice', 'settings', { ...settings, id: `setting-${key}`, operationId: key, fields: { [key]: 'secret' } }));
});
test('real application financial preference names and legacy aliases match the sync allowlist', async () => {
  const applicationPreferences = { financeHidden: 'Sim', financialDay: '31', recentIncomeCategory: 'Salário',
    recentExpenseCategory: 'Alimentação', recentFinanceAccount: 'bank', financeCurrency: 'BRL',
    financeNotifications: 'Sim', financeBudgetAlerts: 'Sim', financeHideValues: 'Sim', financeFirstDay: '31',
    financeRecentIncomeCategory: 'Salário', financeRecentExpenseCategory: 'Alimentação', financeRecentAccount: 'bank' };
  await assertSucceeds(write('alice', 'settings', envelope('alice', 'workspace-settings', 'cloud_settings', {
    date: '', fields: applicationPreferences, amountMinor: 0, status: '',
  })));
});

const bytes = new TextEncoder().encode('private receipt');
const receiptHash = hash(bytes);
const storage = uid => client(uid).storage(`gs://${projectId}.appspot.com`);
const fileRef = (uid, actingUid = uid) => ref(storage(actingUid), `users/${uid}/receipts/${receiptHash}`);
const metadata = uid => ({ contentType: 'text/plain', customMetadata: { ownerUid: uid, sha256: receiptHash } });
test('owner can upload, retrieve, list and delete private storage files', async () => {
  await assertSucceeds(uploadBytes(fileRef('alice'), bytes, metadata('alice')));
  assert.deepEqual(new Uint8Array(await assertSucceeds(getBytes(fileRef('alice')))), bytes);
  const files = await assertSucceeds(list(ref(storage('alice'), 'users/alice/receipts'), { maxResults: 100 }));
  assert.equal(files.items.length, 1);
  await assertSucceeds(deleteObject(fileRef('alice')));
});
test('other users and anonymous clients cannot read, list, upload or delete private files', async () => {
  await uploadBytes(fileRef('bob'), bytes, metadata('bob'));
  await assertFails(getBytes(fileRef('bob', 'alice')));
  await assertFails(list(ref(storage('alice'), 'users/bob/receipts'), { maxResults: 100 }));
  await assertFails(uploadBytes(fileRef('bob', 'alice'), bytes, metadata('bob')));
  await assertFails(deleteObject(fileRef('bob', 'alice')));
  await assertFails(getBytes(ref(env.unauthenticatedContext().storage(`gs://${projectId}.appspot.com`), `users/bob/receipts/${receiptHash}`)));
});
test('storage rejects unverified identity, wrong ownership/hash, unsupported MIME, empty and oversized uploads', async () => {
  const unverified = env.authenticatedContext('alice', { email_verified: false }).storage(`gs://${projectId}.appspot.com`);
  await assertFails(uploadBytes(ref(unverified, `users/alice/receipts/${receiptHash}`), bytes, metadata('alice')));
  await assertFails(uploadBytes(fileRef('alice'), bytes, metadata('bob')));
  await assertFails(uploadBytes(fileRef('alice'), bytes, { ...metadata('alice'), customMetadata: { ownerUid: 'alice', sha256: '0'.repeat(64) } }));
  await assertFails(uploadBytes(fileRef('alice'), bytes, { ...metadata('alice'), contentType: 'application/x-executable' }));
  await assertFails(uploadBytes(fileRef('alice'), new Uint8Array(0), metadata('alice')));
  await assertFails(uploadBytes(fileRef('alice'), new Uint8Array(10000001), metadata('alice')));
});
test('profile photo path accepts only images and unknown storage folders are denied', async () => {
  await assertFails(uploadBytes(ref(storage('alice'), `users/alice/profile/${receiptHash}`), bytes, metadata('alice')));
  await assertFails(uploadBytes(ref(storage('alice'), `users/alice/public/${receiptHash}`), bytes, metadata('alice')));
  await assertSucceeds(uploadBytes(ref(storage('alice'), `users/alice/profile/${receiptHash}`), bytes, { ...metadata('alice'), contentType: 'image/png' }));
});
test('account deletion lock prevents new uploads but still allows private file removal', async () => {
  await uploadBytes(fileRef('alice'), bytes, metadata('alice'));
  await setDoc(root(db('alice'), 'alice'), { deleting: true, updatedAt: serverTimestamp() }, { merge: true });
  await assertFails(uploadBytes(fileRef('alice'), bytes, metadata('alice')));
  await assertSucceeds(deleteObject(fileRef('alice')));
});

test('email registration, verification, sign out, sign in and recovery use the real Auth emulator', async () => {
  const unique = randomUUID().slice(0, 8);
  const app = initializeApp({ apiKey: 'emulator-test-only', projectId }, `auth-${unique}`);
  const authentication = getAuth(app);
  connectAuthEmulator(authentication, 'http://127.0.0.1:9095', { disableWarnings: true });
  const email = `auth-${unique}@example.test`;
  try {
    const created = await createUserWithEmailAndPassword(authentication, email, 'Emulator-only-123');
    assert.equal(created.user.emailVerified, false);
    await sendEmailVerification(created.user);
    const codes = await fetch(`http://127.0.0.1:9095/emulator/v1/projects/${projectId}/oobCodes`).then(response => response.json());
    const verification = codes.oobCodes.find(code => code.email === email && code.requestType === 'VERIFY_EMAIL');
    assert.ok(verification);
    await applyActionCode(authentication, verification.oobCode); await reload(created.user);
    assert.equal(created.user.emailVerified, true);
    const uid = created.user.uid;
    await signOut(authentication); assert.equal(authentication.currentUser, null);
    assert.equal((await signInWithEmailAndPassword(authentication, email, 'Emulator-only-123')).user.uid, uid);
    await sendPasswordResetEmail(authentication, email);
    const resetCodes = await fetch(`http://127.0.0.1:9095/emulator/v1/projects/${projectId}/oobCodes`).then(response => response.json());
    const recovery = resetCodes.oobCodes.find(code => code.email === email && code.requestType === 'PASSWORD_RESET');
    assert.ok(recovery);
    await confirmPasswordReset(authentication, recovery.oobCode, 'Emulator-new-456');
    await signOut(authentication);
    assert.equal((await signInWithEmailAndPassword(authentication, email, 'Emulator-new-456')).user.uid, uid);
  } finally { await deleteApp(app); }
});
test('sensitive Auth changes require reauthentication and email confirmation before account deletion', async () => {
  const unique = randomUUID().slice(0, 8);
  const app = initializeApp({ apiKey: 'emulator-test-only', projectId }, `sensitive-${unique}`);
  const authentication = getAuth(app);
  connectAuthEmulator(authentication, 'http://127.0.0.1:9095', { disableWarnings: true });
  const email = `sensitive-${unique}@example.test`;
  const replacement = `changed-${unique}@example.test`;
  try {
    const created = await createUserWithEmailAndPassword(authentication, email, 'Emulator-only-123');
    await reauthenticateWithCredential(created.user, EmailAuthProvider.credential(email, 'Emulator-only-123'));
    await updatePassword(created.user, 'Emulator-replaced-456');
    await verifyBeforeUpdateEmail(created.user, replacement);
    assert.equal(created.user.email, email);
    const codes = await fetch(`http://127.0.0.1:9095/emulator/v1/projects/${projectId}/oobCodes`).then(response => response.json());
    const change = codes.oobCodes.find(code => code.requestType === 'VERIFY_AND_CHANGE_EMAIL' &&
      (code.email === email || code.email === replacement || code.newEmail === replacement));
    assert.ok(change);
    await applyActionCode(authentication, change.oobCode); await reload(created.user);
    assert.equal(created.user.email, replacement);
    await deleteUser(created.user);
    assert.equal(authentication.currentUser, null);
  } finally { await deleteApp(app); }
});
