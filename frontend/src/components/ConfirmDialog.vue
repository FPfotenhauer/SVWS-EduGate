<script setup lang="ts">
defineProps<{
  open: boolean
  titel: string
  nachricht: string
}>()

const emit = defineEmits<{ confirm: []; cancel: [] }>()
</script>

<template>
  <div v-if="open" class="dialog-overlay" role="presentation" @click.self="emit('cancel')">
    <div class="dialog" role="alertdialog" aria-modal="true" :aria-label="titel">
      <h2>{{ titel }}</h2>
      <p>{{ nachricht }}</p>
      <div class="dialog-actions">
        <button type="button" @click="emit('cancel')">Abbrechen</button>
        <button type="button" class="danger" autofocus @click="emit('confirm')">Deaktivieren</button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dialog-overlay {
  position: fixed;
  inset: 0;
  background: rgb(0 0 0 / 45%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.dialog {
  background: white;
  color: #1a1a1a;
  border-radius: 8px;
  padding: 1.5rem;
  max-width: 28rem;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.75rem;
  margin-top: 1.5rem;
}

.danger {
  background: #b3261e;
  color: white;
  border: none;
  padding: 0.5rem 1rem;
  border-radius: 4px;
}
</style>
