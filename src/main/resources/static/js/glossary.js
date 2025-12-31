document.addEventListener('DOMContentLoaded', function() {
    const messageDiv = document.getElementById('ajax-message');

    function showMessage(message, isSuccess) {
        messageDiv.textContent = message;
        messageDiv.className = 'mb-4 p-4 rounded-lg ' +
            (isSuccess ? 'bg-green-100 text-green-800' : 'bg-red-100 text-red-800');
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
