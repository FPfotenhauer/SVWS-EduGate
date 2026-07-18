<script setup lang="ts">
defineProps<{
  open: boolean
  titel: string
}>()

const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <div v-if="open" class="modal-overlay" role="presentation" @click.self="emit('close')">
    <div class="modal" role="dialog" aria-modal="true" :aria-label="titel" @keydown.esc="emit('close')">
      <div class="modal-header">
        <h2>{{ titel }}</h2>
        <button type="button" class="modal-close" aria-label="Schließen" @click="emit('close')">✕</button>
      </div>
      <div class="modal-body">
        <slot />
      </div>
    </div>
  </div>
</template>

<style scoped>
.modal-overlay {
  position: fixed;
  inset: 0;
  background: var(--overlay);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
  z-index: 100;
}

.modal {
  background: var(--surface);
  color: var(--ink);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 1.5rem;
  width: 100%;
  max-width: 42rem;
  max-height: calc(100vh - 3rem);
  overflow-y: auto;
  box-shadow: var(--shadow);
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}

.modal-header h2 {
  margin: 0;
}

.modal-close {
  background: none;
  border: none;
  color: var(--ink-soft);
  font-size: 1.1rem;
  line-height: 1;
  cursor: pointer;
  padding: 0.25rem;
}

.modal-close:hover {
  color: var(--ink);
}
</style>
