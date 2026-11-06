const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

module.exports = async ({ github, context, core }) => {
  const configPath = process.env.RELEASE_CONFIG || '.github/releases/3.0.json';
  const config = JSON.parse(fs.readFileSync(configPath, 'utf8'));
  const repo = context.repo;
  const { data: source } = await github.rest.actions.getWorkflowRun({ ...repo, run_id: config.source_run });
  if (source.head_sha !== config.source_commit || source.conclusion !== 'success') {
    throw new Error('Refusing to release an unverified source build');
  }
  const jobs = await github.paginate(github.rest.actions.listJobsForWorkflowRun, {
    ...repo, run_id: config.source_run, per_page: 100
  });
  for (const stepName of [
    'Package and verify the Mac app and DMG',
    'Package and verify the Windows app and installer',
    'Verify Windows installation and uninstallation'
  ]) {
    if (!jobs.some(job => job.conclusion === 'success' && job.steps.some(
      step => step.name === stepName && step.conclusion === 'success'
    ))) throw new Error(`Required release check did not pass: ${stepName}`);
  }

  const entries = config.assets.map(name => {
    const data = fs.readFileSync(path.join('release-assets', name));
    if (data.length < 1024 * 1024) throw new Error(`Installer is unexpectedly small: ${name}`);
    return { name, data, digest: `sha256:${crypto.createHash('sha256').update(data).digest('hex')}` };
  });
  const checksums = entries.map(asset => `${asset.digest.slice(7)}  ${asset.name}\n`).join('');
  const checksumData = Buffer.from(checksums);
  entries.push({
    name: 'SHA256SUMS', data: checksumData,
    digest: `sha256:${crypto.createHash('sha256').update(checksumData).digest('hex')}`
  });

  // Never move an existing tag to another source commit.
  try {
    const { data: ref } = await github.rest.git.getRef({ ...repo, ref: `tags/${config.tag}` });
    let object = ref.object;
    while (object.type === 'tag') {
      const { data: tag } = await github.rest.git.getTag({ ...repo, tag_sha: object.sha });
      object = tag.object;
    }
    if (object.type !== 'commit' || object.sha !== config.source_commit) {
      throw new Error('Existing release tag points to different source code');
    }
  } catch (error) {
    if (error.status !== 404) throw error;
  }

  const releases = await github.paginate(github.rest.repos.listReleases, { ...repo, per_page: 100 });
  let release = releases.find(candidate => candidate.tag_name === config.tag);
  if (!release) {
    ({ data: release } = await github.rest.repos.createRelease({
      ...repo, tag_name: config.tag, target_commitish: config.source_commit,
      name: config.title, body: fs.readFileSync(config.notes, 'utf8'),
      draft: true, prerelease: false
    }));
  } else if (release.target_commitish !== config.source_commit) {
    throw new Error('Existing release targets a different source commit');
  }

  for (const entry of entries) {
    const existing = release.assets.find(asset => asset.name === entry.name);
    if (existing) {
      if (existing.digest !== entry.digest) throw new Error(`Existing asset differs: ${entry.name}`);
      continue;
    }
    if (!release.draft) throw new Error('Refusing to modify assets of an already published release');
    const { data: uploaded } = await github.rest.repos.uploadReleaseAsset({
      ...repo, release_id: release.id, name: entry.name, data: entry.data,
      headers: {
        'content-type': entry.name.endsWith('.dmg') ? 'application/x-apple-diskimage'
          : entry.name.endsWith('.exe') ? 'application/vnd.microsoft.portable-executable' : 'text/plain',
        'content-length': entry.data.length
      }
    });
    if (uploaded.size !== entry.data.length || uploaded.digest !== entry.digest) {
      throw new Error(`Uploaded asset verification failed: ${entry.name}`);
    }
  }

  const { data: assets } = await github.rest.repos.listReleaseAssets({ ...repo, release_id: release.id });
  if (!entries.every(entry => assets.some(asset => asset.name === entry.name && asset.digest === entry.digest))) {
    throw new Error('Release assets are incomplete; leaving the release as a draft');
  }
  if (release.draft) {
    ({ data: release } = await github.rest.repos.updateRelease({
      ...repo, release_id: release.id, draft: false, prerelease: false, make_latest: 'true'
    }));
  }
  core.notice(`Published ${release.name}: ${release.html_url}`);
  await core.summary.addHeading(config.title)
    .addLink('Download release', release.html_url)
    .addRaw(`\n\nSource: ${config.source_commit}\n\n`)
    .addCodeBlock(checksums, 'text').write();
};
