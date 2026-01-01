document.addEventListener('DOMContentLoaded', function() {
    const messageDiv = document.getElementById('ajax-message');

    function showMessage(message, isSuccess) {
        messageDiv.textContent = message;
        if (isSuccess) {
            messageDiv.className = 'mb-4 px-4 py-3 text-sm bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-400 border border-green-200 dark:border-green-800 rounded';
        } else {
            messageDiv.className = 'mb-4 px-4 py-3 text-sm bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400 border border-red-200 dark:border-red-800 rounded';
        }
        messageDiv.classList.remove('hidden');
        setTimeout(() => messageDiv.classList.add('hidden'), 3000);
    }

    // Add a synonym
    const addSynonymBtn = document.getElementById('add-synonym-btn');
    if (addSynonymBtn) {
        addSynonymBtn.addEventListener('click', async function() {
            const termName = this.dataset.term;
            const synonymInput = document.getElementById('new-synonym');
            const synonymName = synonymInput.value.trim();

            if (!synonymName) {
                showMessage('동의어를 입력해주세요.', false);
                return;
            }

            try {
                const response = await fetch(`/api/glossary/${encodeURIComponent(termName)}/synonyms`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ synonymName })
                });

                const data = await response.json();
                if (data.success) {
                    location.reload();
                } else {
                    showMessage(data.message, false);
                }
            } catch (error) {
                showMessage('오류가 발생했습니다.', false);
            }
        });
    }

    // Delete synonym
    document.querySelectorAll('.delete-synonym-btn').forEach(btn => {
        btn.addEventListener('click', async function() {
            const termName = this.dataset.term;
            const synonymName = this.dataset.synonym;

            if (!confirm(`동의어 "${synonymName}"을(를) 삭제하시겠습니까?`)) return;

            try {
                const response = await fetch(
                    `/api/glossary/${encodeURIComponent(termName)}/synonyms/${encodeURIComponent(synonymName)}`,
                    { method: 'DELETE' }
                );

                const data = await response.json();
                if (data.success) {
                    location.reload();
                } else {
                    showMessage(data.message, false);
                }
            } catch (error) {
                showMessage('오류가 발생했습니다.', false);
            }
        });
    });
});
