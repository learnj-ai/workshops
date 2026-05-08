# GitHub Actions Workflows

This directory contains GitHub Actions workflows for the LLM Spring Boot Workshop project.

## Workflows

### Deploy Tutorials to GitHub Pages

**File:** `workflows/deploy-tutorials.yml`

**Purpose:** Automatically builds and deploys the unified HonKit tutorial to GitHub Pages.

**Triggers:**
- Push to `main` or `tutorials` branches (when files in `docs/tutorials/` change)
- Pull requests to `main` or `tutorials` branches (build only, no deployment)
- Manual trigger via workflow_dispatch

**Process:**
1. **Build Job:**
   - Checkout repository
   - Setup Node.js 20
   - Install npm dependencies from `docs/tutorials/`
   - Build HonKit static site
   - Upload build artifact

2. **Deploy Job:**
   - Deploy to GitHub Pages (only on push, not PRs)
   - Updates the live tutorial site

**Output:**
The built tutorial is available at: `https://<username>.github.io/<repository>/`

**Requirements:**
- GitHub Pages must be enabled in repository settings
- GitHub Pages source should be set to "GitHub Actions"

## Setup Instructions

### Enable GitHub Pages

1. Go to repository **Settings** → **Pages**
2. Under **Source**, select **GitHub Actions**
3. The workflow will automatically deploy on the next push

### Manual Deployment

You can manually trigger the deployment:

1. Go to **Actions** tab in the repository
2. Select **Deploy Tutorials to GitHub Pages** workflow
3. Click **Run workflow**
4. Select the branch and click **Run workflow**

## Monitoring

### Check Build Status

- View workflow runs: **Actions** tab → **Deploy Tutorials to GitHub Pages**
- Check build logs for errors or warnings
- Verify deployment URL in the deploy job output

### Troubleshooting

**Build fails with "Cannot find module":**
- The workflow uses `npm ci` which requires a `package-lock.json`
- Run `npm install` locally in `docs/tutorials/` and commit the lockfile

**Deployment fails with permissions error:**
- Verify GitHub Pages is enabled in repository settings
- Check that Pages source is set to "GitHub Actions"
- Ensure the workflow has proper permissions (already configured in workflow file)

**Changes not appearing on site:**
- Wait a few minutes for GitHub Pages CDN to update
- Hard refresh your browser (Ctrl+Shift+R or Cmd+Shift+R)
- Check the Actions tab to ensure deployment succeeded

## Badge

Add this badge to your README to show deployment status:

```markdown
[![Deploy Tutorials](https://github.com/<username>/<repository>/actions/workflows/deploy-tutorials.yml/badge.svg)](https://github.com/<username>/<repository>/actions/workflows/deploy-tutorials.yml)
```

Replace `<username>` and `<repository>` with your GitHub username and repository name.
