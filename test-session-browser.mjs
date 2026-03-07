#!/usr/bin/env node
/**
 * Browser-based test to detect phantom JSESSIONID cookie changes.
 * Uses Playwright to run a real browser, login, interact with the app,
 * and monitor all Set-Cookie headers on every response.
 *
 * Usage:
 *   cd /tmp && npm install playwright && npx playwright install chromium
 *   node test-session-browser.mjs
 *
 * This test was created to diagnose a session logout bug where stale
 * push requests caused phantom JSESSIONID cookies to overwrite the
 * authenticated session. See notes/LOGOUT.md for details.
 */
import { chromium } from 'playwright';

const BASE_URL = 'http://localhost:8080';
const USERNAME = 'alice';
const PASSWORD = 'alice';

let authCookie = null;
let phantomDetected = false;
const cookieLog = [];

async function run() {
    const browser = await chromium.launch({ headless: true });
    const context = await browser.newContext();
    const page = await context.newPage();

    // Monitor ALL responses for Set-Cookie headers
    page.on('response', async (response) => {
        const headers = response.headers();
        const setCookie = headers['set-cookie'];
        const url = response.url();
        const status = response.status();

        if (setCookie && setCookie.includes('JSESSIONID')) {
            const match = setCookie.match(/JSESSIONID=([^;]+)/);
            const newId = match ? match[1] : 'unknown';
            const short = newId.substring(0, 16);
            const urlPath = new URL(url).pathname;

            const entry = {
                time: new Date().toISOString().split('T')[1],
                url: urlPath,
                status,
                cookie: short,
                full: newId
            };
            cookieLog.push(entry);

            if (authCookie && newId !== authCookie) {
                console.log(`\n*** PHANTOM COOKIE DETECTED ***`);
                console.log(`    URL: ${response.request().method()} ${urlPath}`);
                console.log(`    Status: ${status}`);
                console.log(`    Auth cookie: ${authCookie.substring(0, 16)}...`);
                console.log(`    New cookie:  ${short}...`);
                console.log(`    Set-Cookie:  ${setCookie}`);
                phantomDetected = true;
            } else {
                console.log(`  [cookie] ${urlPath} → ${short}... (${authCookie ? 'same' : 'initial'})`);
            }
        }
    });

    // Monitor requests to see what cookie the browser sends
    page.on('request', (request) => {
        const cookies = request.headers()['cookie'];
        if (cookies && request.url().includes('/VAADIN/push')) {
            const match = cookies.match(/JSESSIONID=([^;]+)/);
            if (match) {
                const sent = match[1].substring(0, 16);
                if (authCookie && match[1] !== authCookie) {
                    console.log(`\n*** BROWSER SENDING WRONG COOKIE ***`);
                    console.log(`    URL: ${request.method()} ${new URL(request.url()).pathname}`);
                    console.log(`    Expected: ${authCookie.substring(0, 16)}...`);
                    console.log(`    Sending:  ${sent}...`);
                    phantomDetected = true;
                }
            }
        }
    });

    try {
        // Step 0: Simulate stale cookie from a previous server run
        console.log('=== Browser Session Cookie Test ===\n');
        console.log('[0] Setting stale JSESSIONID (simulating server restart)...');
        await context.addCookies([{
            name: 'JSESSIONID',
            value: 'STALE_FROM_PREVIOUS_SERVER_RUN_ABC123',
            domain: 'localhost',
            path: '/',
            httpOnly: true,
        }]);

        // Step 1: Navigate to app (should redirect to login)
        console.log('[1] Navigating to app (with stale cookie)...');
        await page.goto(BASE_URL, { waitUntil: 'domcontentloaded' });
        await page.waitForTimeout(3000);

        // Step 2: Login
        console.log('[2] Logging in...');
        await page.fill('input[name="username"]', USERNAME);
        await page.fill('input[name="password"]', PASSWORD);
        await page.click('vaadin-button[slot="submit"], button[type="submit"]');
        await page.waitForTimeout(3000);

        // Capture the authenticated cookie
        const cookies = await context.cookies();
        const jsessionid = cookies.find(c => c.name === 'JSESSIONID');
        if (jsessionid) {
            authCookie = jsessionid.value;
            console.log(`    Authenticated: ${authCookie.substring(0, 16)}...`);
        } else {
            console.log('    ERROR: No JSESSIONID cookie found!');
            await browser.close();
            return;
        }

        // Step 3: Interact with the app to trigger push activity
        console.log('\n[3] Interacting with app (sending chat messages)...');

        const selectors = [
            'vaadin-message-input input',
            'vaadin-message-input textarea',
            'vaadin-text-field input',
            'vaadin-text-area textarea',
            'input[type="text"]:not([name="username"])',
            'textarea',
        ];

        let chatInput = null;
        for (const sel of selectors) {
            chatInput = await page.$(sel);
            if (chatInput) {
                console.log(`    Using chat input: ${sel}`);
                break;
            }
        }

        const messages = ['hello', 'who am i', 'tell me about cats', 'what do you know about me'];
        if (chatInput) {
            for (let i = 0; i < messages.length; i++) {
                await page.waitForTimeout(1000);
                console.log(`    Sending: "${messages[i]}"...`);
                try {
                    await chatInput.fill(messages[i]);
                    await page.keyboard.press('Enter');
                } catch(e) {
                    console.log(`    Fill failed, retrying with type...`);
                    await chatInput.click();
                    await page.keyboard.type(messages[i]);
                    await page.keyboard.press('Enter');
                }

                console.log(`    Waiting 15s for response + background processing...`);
                for (let j = 0; j < 15; j++) {
                    await page.waitForTimeout(1000);
                    const currentCookies = await context.cookies();
                    const current = currentCookies.find(c => c.name === 'JSESSIONID');
                    const currentUrl = page.url();

                    if (currentUrl.includes('/login')) {
                        console.log(`\n*** REDIRECTED TO LOGIN at message ${i + 1}, ${j + 1}s ***`);
                        phantomDetected = true;
                        break;
                    }
                    if (current && current.value !== authCookie) {
                        console.log(`\n*** COOKIE CHANGED at message ${i + 1}, ${j + 1}s ***`);
                        console.log(`    Was: ${authCookie.substring(0, 16)}...`);
                        console.log(`    Now: ${current.value.substring(0, 16)}...`);
                        phantomDetected = true;
                        break;
                    }
                }
                if (phantomDetected) break;
            }
        } else {
            console.log('    No chat input found, waiting for push activity...');
        }

        // Step 4: Simulate tab/window switching
        console.log('\n[4] Simulating window switching...');
        for (let cycle = 0; cycle < 5 && !phantomDetected; cycle++) {
            console.log(`\n  --- Switch cycle ${cycle + 1} ---`);

            if (chatInput) {
                const msg = `message before switch ${cycle + 1}`;
                console.log(`    Sending "${msg}"...`);
                try {
                    await chatInput.fill(msg);
                    await page.keyboard.press('Enter');
                } catch(e) { /* input may be disabled */ }
                await page.waitForTimeout(2000);
            }

            console.log('    Simulating tab hidden (backgrounding)...');
            await page.evaluate(() => {
                Object.defineProperty(document, 'hidden', { value: true, configurable: true });
                Object.defineProperty(document, 'visibilityState', { value: 'hidden', configurable: true });
                document.dispatchEvent(new Event('visibilitychange'));
            });

            const page2 = await context.newPage();
            await page2.goto('about:blank');
            console.log('    Waiting 10s in background...');
            await page2.waitForTimeout(10000);

            const bgCookies = await context.cookies();
            const bgCookie = bgCookies.find(c => c.name === 'JSESSIONID');
            if (bgCookie && bgCookie.value !== authCookie) {
                console.log(`\n*** COOKIE CHANGED WHILE BACKGROUNDED ***`);
                console.log(`    Was: ${authCookie.substring(0, 16)}...`);
                console.log(`    Now: ${bgCookie.value.substring(0, 16)}...`);
                phantomDetected = true;
            }

            await page2.close();
            await page.bringToFront();
            console.log('    Tab restored to foreground...');
            await page.evaluate(() => {
                Object.defineProperty(document, 'hidden', { value: false, configurable: true });
                Object.defineProperty(document, 'visibilityState', { value: 'visible', configurable: true });
                document.dispatchEvent(new Event('visibilitychange'));
            });

            for (let j = 0; j < 10; j++) {
                await page.waitForTimeout(1000);
                const currentCookies = await context.cookies();
                const current = currentCookies.find(c => c.name === 'JSESSIONID');
                const currentUrl = page.url();

                if (currentUrl.includes('/login')) {
                    console.log(`\n*** REDIRECTED TO LOGIN after switch cycle ${cycle + 1}, +${j + 1}s ***`);
                    phantomDetected = true;
                    break;
                }
                if (current && current.value !== authCookie) {
                    console.log(`\n*** COOKIE CHANGED after switch cycle ${cycle + 1}, +${j + 1}s ***`);
                    console.log(`    Was: ${authCookie.substring(0, 16)}...`);
                    console.log(`    Now: ${current.value.substring(0, 16)}...`);
                    phantomDetected = true;
                    break;
                }
            }
        }

        // Step 5: Final monitoring
        console.log('\n[5] Monitoring for spontaneous cookie changes (60s)...');
        for (let i = 0; i < 12; i++) {
            await page.waitForTimeout(5000);
            const currentCookies = await context.cookies();
            const current = currentCookies.find(c => c.name === 'JSESSIONID');
            const currentUrl = page.url();

            if (currentUrl.includes('/login')) {
                console.log(`\n*** REDIRECTED TO LOGIN PAGE at ${5 * (i + 1)}s ***`);
                phantomDetected = true;
                break;
            }
            if (current && current.value !== authCookie) {
                console.log(`\n*** COOKIE CHANGED at ${5 * (i + 1)}s ***`);
                console.log(`    Was: ${authCookie.substring(0, 16)}...`);
                console.log(`    Now: ${current.value.substring(0, 16)}...`);
                phantomDetected = true;
                break;
            }
            process.stdout.write(`  [${5 * (i + 1)}s] OK `);
        }

    } catch (err) {
        console.error('Error:', err.message);
    }

    console.log('\n\n=== Cookie Change Log ===');
    for (const entry of cookieLog) {
        console.log(`  ${entry.time} ${entry.url} → ${entry.cookie}...`);
    }

    console.log(`\n=== RESULT: ${phantomDetected ? 'PHANTOM COOKIE DETECTED' : 'No phantom cookies'} ===`);
    process.exit(phantomDetected ? 1 : 0);

    await browser.close();
}

run().catch(console.error);
