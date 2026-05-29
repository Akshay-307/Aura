/* ==========================================================================
   AURA Landing Page — JavaScript
   GitHub Release integration, Mobile Drawer, FAQ, and App Simulator State
   ========================================================================== */

(function() {
    'use strict';

    // --- Configuration ---
    const CONFIG = {
        githubRepo: 'Akshay-307/Aura-Releases',
        githubApiUrl: 'https://api.github.com/repos/Akshay-307/Aura-Releases/releases/latest',
        githubReleasesUrl: 'https://github.com/Akshay-307/Aura-Releases/releases',
        githubDirectDownloadUrl: 'https://github.com/Akshay-307/Aura-Releases/releases/latest/download/app-release.apk',
        defaultVersion: 'v1.0.1',
        defaultSize: '18.4 MB',
        defaultDate: 'May 12, 2026'
    };

    // --- DOM Loaded Hook ---
    document.addEventListener('DOMContentLoaded', init);

    function init() {
        initNavigation();
        initMobileDrawer();
        initFAQAccordion();
        initScrollReveal();
        initLegalModal();
        initParticles();
        fetchGitHubRelease();
        initSmoothScrolling();
        initBackToTop();
    }

    // --- 1. Dynamic GitHub Release Fetching ---
    async function fetchGitHubRelease() {
        const releaseTag = document.getElementById('release-tag');
        const heroStatVersion = document.getElementById('hero-stat-version');
        const releaseSize = document.getElementById('release-size');
        const releaseDate = document.getElementById('release-date');
        const downloadBtn = document.getElementById('apk-download-btn');
        const downloadBtnText = document.getElementById('download-btn-text');
        const notesWrapper = document.getElementById('release-notes-wrapper');
        const notesContent = document.getElementById('release-notes-content');

        try {
            const response = await fetch(CONFIG.githubApiUrl);
            if (!response.ok) {
                throw new Error(`GitHub API returned status ${response.status}`);
            }

            const data = await response.json();
            
            // Extract version details
            const version = data.tag_name || CONFIG.defaultVersion;
            const publishDateRaw = data.published_at;
            let formattedDate = CONFIG.defaultDate;
            if (publishDateRaw) {
                const dateObj = new Date(publishDateRaw);
                formattedDate = dateObj.toLocaleDateString('en-US', {
                    year: 'numeric',
                    month: 'long',
                    day: 'numeric'
                });
            }

            // Find APK Asset
            let apkAsset = null;
            if (data.assets && data.assets.length > 0) {
                apkAsset = data.assets.find(asset => asset.name.toLowerCase().endsWith('.apk'));
            }

            let downloadUrl = CONFIG.githubReleasesUrl;
            let sizeStr = CONFIG.defaultSize;

            if (apkAsset) {
                downloadUrl = apkAsset.browser_download_url;
                sizeStr = formatBytes(apkAsset.size);
            } else {
                downloadUrl = data.html_url || CONFIG.githubReleasesUrl;
            }

            // Update UI
            releaseTag.textContent = version;
            heroStatVersion.textContent = version;
            releaseSize.textContent = sizeStr;
            releaseDate.textContent = formattedDate;
            downloadBtn.href = downloadUrl;

            if (apkAsset) {
                downloadBtnText.textContent = `Download APK (${sizeStr})`;
            } else {
                downloadBtnText.textContent = 'Get Latest Release on GitHub';
            }

            // Populate Changelog / Release Notes
            if (data.body && data.body.trim()) {
                notesWrapper.style.display = 'block';
                notesContent.textContent = data.body;
            }

        } catch (error) {
            console.warn('Unable to load dynamic GitHub releases, using static fallback:', error);
            
            // Fallback UI State
            releaseTag.textContent = CONFIG.defaultVersion;
            heroStatVersion.textContent = CONFIG.defaultVersion;
            releaseSize.textContent = CONFIG.defaultSize;
            releaseDate.textContent = CONFIG.defaultDate;
            downloadBtn.href = CONFIG.githubDirectDownloadUrl;
            downloadBtnText.textContent = `Download Latest APK (${CONFIG.defaultSize})`;
        }
    }

    // Byte Formatter Helper
    function formatBytes(bytes, decimals = 1) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const dm = decimals < 0 ? 0 : decimals;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(dm)) + ' ' + sizes[i];
    }

    // --- 3. Navigation Header Scroll Effect ---
    function initNavigation() {
        const header = document.getElementById('header');
        window.addEventListener('scroll', () => {
            if (window.scrollY > 40) {
                header.classList.add('scrolled');
            } else {
                header.classList.remove('scrolled');
            }
        }, { passive: true });
    }

    // --- 4. Mobile Drawer toggle ---
    function initMobileDrawer() {
        const hamburger = document.getElementById('nav-hamburger');
        const drawer = document.getElementById('mobile-drawer');
        const links = drawer.querySelectorAll('.mobile-drawer-link, .mobile-drawer-btn');

        function toggleDrawer() {
            const isOpen = hamburger.classList.toggle('active');
            drawer.classList.toggle('active');
            drawer.setAttribute('aria-hidden', !isOpen);
            document.body.style.overflow = isOpen ? 'hidden' : '';
        }

        function closeDrawer() {
            hamburger.classList.remove('active');
            drawer.classList.remove('active');
            drawer.setAttribute('aria-hidden', 'true');
            document.body.style.overflow = '';
        }

        hamburger.addEventListener('click', toggleDrawer);

        links.forEach(link => {
            link.addEventListener('click', closeDrawer);
        });

        // Close on escape key
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && drawer.classList.contains('active')) {
                closeDrawer();
            }
        });
    }

    // --- 5. FAQ Accordion ---
    function initFAQAccordion() {
        const faqItems = document.querySelectorAll('.faq-item');

        faqItems.forEach(item => {
            const trigger = item.querySelector('.faq-trigger');
            const panel = item.querySelector('.faq-panel');

            trigger.addEventListener('click', () => {
                const isActive = item.classList.contains('active');

                // Collapse all other items first
                faqItems.forEach(other => {
                    if (other !== item) {
                        other.classList.remove('active');
                        other.querySelector('.faq-trigger').setAttribute('aria-expanded', 'false');
                        other.querySelector('.faq-panel').style.maxHeight = null;
                    }
                });

                // Toggle current item
                item.classList.toggle('active', !isActive);
                trigger.setAttribute('aria-expanded', !isActive);

                if (!isActive) {
                    panel.style.maxHeight = panel.scrollHeight + 'px';
                } else {
                    panel.style.maxHeight = null;
                }
            });
        });
    }

    // --- 6. Scroll Reveal Observer ---
    function initScrollReveal() {
        const elements = document.querySelectorAll('.feature-card, .section-heading-block, .download-card-container, .faq-item, .hero-content, .hero-visual');
        
        elements.forEach(el => {
            el.classList.add('reveal');
        });

        const revealObserver = new IntersectionObserver((entries, observer) => {
            entries.forEach(entry => {
                if (entry.isIntersecting) {
                    entry.target.classList.add('visible');
                    observer.unobserve(entry.target);
                }
            });
        }, {
            threshold: 0.1,
            rootMargin: '0px 0px -40px 0px'
        });

        elements.forEach(el => {
            revealObserver.observe(el);
        });
    }

    // --- 7. Legal Modal (DMCA / Disclaimer) ---
    function initLegalModal() {
        const modal = document.getElementById('legal-modal');
        const trigger = document.getElementById('legal-trigger');
        const closeBtn = document.getElementById('legal-close-btn');

        function openModal() {
            modal.classList.add('active');
            modal.setAttribute('aria-hidden', 'false');
            document.body.style.overflow = 'hidden';
        }

        function closeModal() {
            modal.classList.remove('active');
            modal.setAttribute('aria-hidden', 'true');
            document.body.style.overflow = '';
        }

        trigger.addEventListener('click', openModal);
        closeBtn.addEventListener('click', closeModal);

        modal.addEventListener('click', (e) => {
            if (e.target === modal) closeModal();
        });

        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && modal.classList.contains('active')) {
                closeModal();
            }
        });
    }

    // --- 8. Dynamic Ambient Particles ---
    function initParticles() {
        const container = document.getElementById('particles-container');
        if (!container) return;

        const particleCount = 20;
        const fragment = document.createDocumentFragment();

        for (let i = 0; i < particleCount; i++) {
            const particle = document.createElement('div');
            particle.classList.add('bg-particle');
            
            // Random styling for floating coordinates
            const size = Math.floor(Math.random() * 4) + 2; // 2px - 6px
            const posX = Math.floor(Math.random() * 100);
            const posY = Math.floor(Math.random() * 100);
            const delay = Math.random() * 10;
            const duration = Math.random() * 15 + 15; // 15s - 30s

            particle.style.cssText = `
                position: absolute;
                width: ${size}px;
                height: ${size}px;
                background-color: ${Math.random() > 0.5 ? 'var(--accent)' : 'var(--primary-light)'};
                border-radius: 50%;
                opacity: ${Math.random() * 0.4 + 0.15};
                left: ${posX}%;
                top: ${posY}%;
                pointer-events: none;
                animation: floatParticle ${duration}s linear infinite;
                animation-delay: -${delay}s;
            `;

            fragment.appendChild(particle);
        }

        container.appendChild(fragment);

        // Inject keyframes dynamically
        const styleSheet = document.createElement('style');
        styleSheet.textContent = `
            @keyframes floatParticle {
                0% { transform: translateY(0) translateX(0); opacity: 0; }
                10% { opacity: 0.4; }
                90% { opacity: 0.4; }
                100% { transform: translateY(-100px) translateX(30px); opacity: 0; }
            }
        `;
        document.head.appendChild(styleSheet);
    }

    // --- 9. Smooth Scrolling links ---
    function initSmoothScrolling() {
        const anchors = document.querySelectorAll('a[href^="#"]');
        anchors.forEach(anchor => {
            anchor.addEventListener('click', function(e) {
                const targetId = this.getAttribute('href');
                if (targetId === '#') return;
                
                const targetElement = document.querySelector(targetId);
                if (targetElement) {
                    e.preventDefault();
                    targetElement.scrollIntoView({ behavior: 'smooth' });
                }
            });
        });
    }

    // --- 10. Back To Top ---
    function initBackToTop() {
        const bttBtn = document.getElementById('back-to-top');
        if (!bttBtn) return;

        window.addEventListener('scroll', () => {
            if (window.scrollY > 400) {
                bttBtn.classList.add('visible');
            } else {
                bttBtn.classList.remove('visible');
            }
        }, { passive: true });

        bttBtn.addEventListener('click', () => {
            window.scrollTo({
                top: 0,
                behavior: 'smooth'
            });
        });
    }

})();
