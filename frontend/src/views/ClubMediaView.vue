<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchClubById } from '../services/clubService'
import { fetchClubMediaFeed, fetchClubPostComments } from '../services/clubPostService'
import type { Club } from '../types/club'
import type { ClubPost, ClubPostComment } from '../types/clubPost'
import { clubPostImage } from '../utils/clubImages'
import { userAvatar } from '../utils/avatarImages'
import BackButton from '../components/BackButton.vue'

const DEFAULT_PAGE_SIZE = 12

interface CommentsEntry {
  loading: boolean
  error: string
  comments: ClubPostComment[]
}

const route = useRoute()
const router = useRouter()

const club = ref<Club | null>(null)
const posts = ref<ClubPost[]>([])
const page = ref(0)
const size = ref(DEFAULT_PAGE_SIZE)
const total = ref(0)
const loading = ref(true)
const error = ref('')

const expandedPostIds = ref<Set<number>>(new Set())
const commentsByPost = ref<Record<number, CommentsEntry>>({})
let loadedClubId: string | null = null

const routeClubId = computed(() => {
  const raw = route.params.id
  const value = Array.isArray(raw) ? raw[0] : raw
  return value ?? ''
})

const commentsRegionId = (postId: number) => `club-media-comments-${postId}`

const backTarget = computed(() => (routeClubId.value ? `/clubs/${routeClubId.value}` : '/'))

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / Math.max(size.value, 1))))
const hasNextPage = computed(() => (page.value + 1) * size.value < total.value)
const hasPreviousPage = computed(() => page.value > 0)

const parseQueryInt = (raw: unknown, fallback: number) => {
  const value = Array.isArray(raw) ? raw[0] : raw
  const parsed = value !== undefined && value !== null ? Number(value) : NaN
  return Number.isFinite(parsed) ? Math.trunc(parsed) : fallback
}

const load = async () => {
  const clubIdOrSlug = routeClubId.value
  if (!clubIdOrSlug) {
    return
  }

  loading.value = true
  error.value = ''
  expandedPostIds.value = new Set()
  commentsByPost.value = {}

  const requestedPage = Math.max(0, parseQueryInt(route.query.page, 0))
  const requestedSize = Math.max(1, parseQueryInt(route.query.size, DEFAULT_PAGE_SIZE))
  // Pagination re-runs this on every page/size query change but must not re-fetch a club
  // detail record that hasn't changed -- only the feed page itself is new.
  const needsClub = loadedClubId !== clubIdOrSlug

  try {
    const [clubResponse, feed] = await Promise.all([
      needsClub ? fetchClubById(clubIdOrSlug) : Promise.resolve(club.value),
      fetchClubMediaFeed(clubIdOrSlug, requestedPage, requestedSize),
    ])
    if (needsClub) {
      club.value = clubResponse
      loadedClubId = clubIdOrSlug
    }
    posts.value = feed.items
    page.value = feed.page
    size.value = feed.size
    total.value = feed.total
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load club media'
    club.value = null
    posts.value = []
    loadedClubId = null
  } finally {
    loading.value = false
  }
}

const retryLoad = () => {
  void load()
}

const goToPage = (nextPage: number) => {
  if (nextPage < 0) {
    return
  }
  void router.push({ query: { ...route.query, page: String(nextPage), size: String(size.value) } })
}

const isExpanded = (postId: number) => expandedPostIds.value.has(postId)

const emptyCommentsEntry: CommentsEntry = { loading: false, error: '', comments: [] }
const commentsState = (postId: number) => commentsByPost.value[postId] ?? emptyCommentsEntry

const loadComments = async (postId: number) => {
  commentsByPost.value = {
    ...commentsByPost.value,
    [postId]: { loading: true, error: '', comments: [] },
  }
  try {
    const comments = await fetchClubPostComments(routeClubId.value, postId)
    commentsByPost.value = {
      ...commentsByPost.value,
      [postId]: { loading: false, error: '', comments },
    }
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Failed to load comments'
    commentsByPost.value = {
      ...commentsByPost.value,
      [postId]: { loading: false, error: message, comments: [] },
    }
  }
}

const toggleComments = (postId: number) => {
  const next = new Set(expandedPostIds.value)
  if (next.has(postId)) {
    next.delete(postId)
    expandedPostIds.value = next
    return
  }
  next.add(postId)
  expandedPostIds.value = next
  const existing = commentsByPost.value[postId]
  if (!existing || existing.error) {
    void loadComments(postId)
  }
}

const relativeTimeFormatter = new Intl.RelativeTimeFormat('en', { numeric: 'auto' })
const relativeTimeDivisions: Array<[Intl.RelativeTimeFormatUnit, number]> = [
  ['year', 60 * 60 * 24 * 365],
  ['month', 60 * 60 * 24 * 30],
  ['week', 60 * 60 * 24 * 7],
  ['day', 60 * 60 * 24],
  ['hour', 60 * 60],
  ['minute', 60],
  ['second', 1],
]

// post.createdAt is a java.time.Instant on the backend (see PublicClubPost's Javadoc), which
// Jackson always serializes with an explicit UTC offset (a trailing "Z"). That contract is what
// makes a plain `new Date(iso)` safe here: there is no ambiguous, offset-less wall-clock string
// left for a browser to misparse as its own local time.
const formatRelativeTime = (iso: string) => {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const diffSeconds = Math.round((date.getTime() - Date.now()) / 1000)
  for (const [unit, secondsInUnit] of relativeTimeDivisions) {
    if (Math.abs(diffSeconds) >= secondsInUnit || unit === 'second') {
      return relativeTimeFormatter.format(Math.round(diffSeconds / secondsInUnit), unit)
    }
  }
  return ''
}

let previousRobotsContent: string | null = null
let createdRobotsMeta = false

onMounted(() => {
  const existing = document.head.querySelector('meta[name="robots"]')
  if (existing) {
    previousRobotsContent = existing.getAttribute('content')
    existing.setAttribute('content', 'noindex')
  } else {
    const meta = document.createElement('meta')
    meta.setAttribute('name', 'robots')
    meta.setAttribute('content', 'noindex')
    document.head.appendChild(meta)
    createdRobotsMeta = true
  }
})

onBeforeUnmount(() => {
  const existing = document.head.querySelector('meta[name="robots"]')
  if (!existing) {
    return
  }
  if (createdRobotsMeta) {
    existing.remove()
  } else if (previousRobotsContent !== null) {
    existing.setAttribute('content', previousRobotsContent)
  }
})

watch(
  () => [routeClubId.value, route.query.page, route.query.size],
  () => {
    void load()
  },
  { immediate: true },
)
</script>

<template>
  <section class="club-media page-shell">
    <BackButton :fallback-to="backTarget">← Back to club</BackButton>

    <div v-if="loading" class="mv-status">Loading club media…</div>

    <div v-else-if="error" class="mv-status mv-status--error">
      <p>{{ error }}</p>
      <button type="button" class="mv-retry-btn" @click="retryLoad">Try again</button>
    </div>

    <template v-else>
      <header class="mv-header">
        <p class="section-label" v-if="club">Media · {{ club.name }}</p>
        <h1>Club media</h1>
        <p class="mv-subtitle">Activity photos and updates shared by club members.</p>
      </header>

      <p v-if="posts.length === 0" class="mv-empty">No posts yet. Check back soon.</p>

      <ul v-else class="mv-post-list">
        <li v-for="post in posts" :key="post.id" class="mv-post-card">
          <div class="mv-post-media">
            <img :src="clubPostImage(post)" :alt="post.title" loading="lazy" class="mv-post-image" />
            <span v-if="post.pinnedAt" class="mv-badge-pinned">Pinned</span>
          </div>
          <div class="mv-post-body">
            <h2 class="mv-post-title">{{ post.title }}</h2>
            <div class="mv-post-author">
              <img
                :src="userAvatar(post.authorAvatarUrl, post.authorDisplayName)"
                :alt="post.authorDisplayName"
                loading="lazy"
                class="mv-author-avatar"
              />
              <div>
                <p class="mv-author-name">{{ post.authorDisplayName }}</p>
                <p class="mv-post-time">{{ formatRelativeTime(post.createdAt) }}</p>
              </div>
            </div>
            <button
              type="button"
              class="mv-comments-toggle"
              :aria-expanded="isExpanded(post.id)"
              :aria-controls="commentsRegionId(post.id)"
              @click="toggleComments(post.id)"
            >
              {{ isExpanded(post.id) ? 'Hide comments' : `Show comments (${post.commentCount})` }}
            </button>
            <div
              v-show="isExpanded(post.id)"
              :id="commentsRegionId(post.id)"
              class="mv-comments"
              role="region"
              :aria-label="`Comments on ${post.title}`"
            >
              <p v-if="commentsState(post.id).loading" class="mv-status">Loading comments…</p>
              <p v-else-if="commentsState(post.id).error" class="mv-status mv-status--error">
                {{ commentsState(post.id).error }}
              </p>
              <p v-else-if="commentsState(post.id).comments.length === 0" class="mv-empty">
                No comments yet.
              </p>
              <ul v-else class="mv-comment-list">
                <li v-for="comment in commentsState(post.id).comments" :key="comment.id" class="mv-comment">
                  <p class="mv-comment-author">{{ comment.authorDisplayName }}</p>
                  <p class="mv-comment-body">{{ comment.body }}</p>
                  <p class="mv-comment-time">{{ formatRelativeTime(comment.createdAt) }}</p>
                </li>
              </ul>
            </div>
          </div>
        </li>
      </ul>

      <nav v-if="posts.length" class="mv-pagination">
        <button type="button" :disabled="!hasPreviousPage" @click="goToPage(page - 1)">Previous</button>
        <span class="mv-pagination-status">Page {{ page + 1 }} of {{ totalPages }}</span>
        <button type="button" :disabled="!hasNextPage" @click="goToPage(page + 1)">Next</button>
      </nav>
    </template>
  </section>
</template>

<style scoped>
.club-media {
  display: flex;
  flex-direction: column;
  gap: 1.5rem;
  padding-block: clamp(2rem, 5vw, 3.5rem);
}

.mv-status {
  border-radius: 20px;
  border: 1px solid var(--mv-border);
  padding: 1rem 1.25rem;
  background: var(--mv-surface-card-strong);
}

.mv-status--error {
  border-color: rgba(248, 113, 113, 0.35);
  color: var(--mv-status-danger);
}

.mv-retry-btn {
  border-radius: 999px;
  border: 1px solid var(--mv-border-strong);
  background: var(--mv-surface-accent);
  color: var(--mv-gold);
  font-weight: 600;
  padding: 0.4rem 1.2rem;
  cursor: pointer;
  margin-top: 0.5rem;
}

.mv-header h1 {
  margin: 0.25rem 0 0.35rem;
  font-size: clamp(1.8rem, 3.5vw, 2.6rem);
}

.mv-subtitle {
  margin: 0;
  color: var(--mv-text-faint);
}

.mv-empty {
  border-radius: 20px;
  border: 1px dashed var(--mv-border);
  padding: 1.5rem;
  text-align: center;
  color: var(--mv-text-faint);
}

.mv-post-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1.5rem;
  list-style: none;
  margin: 0;
  padding: 0;
}

.mv-post-card {
  border-radius: 24px;
  border: 1px solid var(--mv-border);
  background: var(--mv-surface-card);
  overflow: hidden;
  box-shadow: var(--mv-shadow-card);
  display: flex;
  flex-direction: column;
}

.mv-post-media {
  position: relative;
}

.mv-post-image {
  width: 100%;
  aspect-ratio: 4 / 3;
  object-fit: cover;
  display: block;
}

.mv-badge-pinned {
  position: absolute;
  top: 0.75rem;
  left: 0.75rem;
  border-radius: 999px;
  padding: 0.25rem 0.75rem;
  background: var(--mv-gold);
  color: var(--mv-primary-text);
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.03em;
  text-transform: uppercase;
}

.mv-post-body {
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.mv-post-title {
  margin: 0;
  font-size: 1.15rem;
}

.mv-post-author {
  display: flex;
  align-items: center;
  gap: 0.65rem;
}

.mv-author-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
  flex-shrink: 0;
}

.mv-author-name {
  margin: 0;
  font-weight: 600;
}

.mv-post-time {
  margin: 0;
  color: var(--mv-text-dim);
  font-size: 0.85rem;
}

.mv-comments-toggle {
  align-self: flex-start;
  border: 1px solid var(--mv-border);
  border-radius: 999px;
  padding: 0.35rem 0.9rem;
  background: var(--mv-surface-muted);
  color: var(--mv-text-soft);
  font-weight: 600;
  cursor: pointer;
}

.mv-comments {
  border-top: 1px solid var(--mv-border);
  padding-top: 0.75rem;
}

.mv-comment-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.mv-comment {
  border-radius: 14px;
  background: var(--mv-surface-muted);
  padding: 0.6rem 0.8rem;
}

.mv-comment-author {
  margin: 0;
  font-weight: 600;
}

.mv-comment-body {
  margin: 0.15rem 0;
}

.mv-comment-time {
  margin: 0;
  color: var(--mv-text-dim);
  font-size: 0.8rem;
}

.mv-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
}

.mv-pagination button {
  border-radius: 999px;
  border: 1px solid var(--mv-border-strong);
  background: var(--mv-surface-accent);
  color: var(--mv-gold);
  font-weight: 600;
  padding: 0.45rem 1.2rem;
  cursor: pointer;
}

.mv-pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.mv-pagination-status {
  color: var(--mv-text-faint);
}

@media (max-width: 900px) {
  .mv-post-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .mv-post-card {
    border-radius: 20px;
  }

  .mv-post-body {
    padding: 1rem;
  }
}

@media (max-width: 640px) {
  .mv-pagination {
    flex-wrap: wrap;
  }
}

@media (max-width: 480px) {
  .mv-header h1 {
    font-size: 1.5rem;
  }

  .mv-post-body {
    padding: 0.85rem;
  }

  .mv-pagination button {
    flex: 1 1 auto;
    text-align: center;
  }
}
</style>
