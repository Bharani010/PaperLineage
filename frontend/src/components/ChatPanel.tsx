import { useEffect, useRef, useState, type KeyboardEvent } from 'react';
import type { ChatMessage } from '../types';

type WsStatus = 'disconnected' | 'connecting' | 'connected' | 'error';

interface Props {
  messages: ChatMessage[];
  status: WsStatus;
  onSend: (text: string) => void;
  onClear: () => void;
}

export function ChatPanel({ messages, status, onSend, onClear }: Props) {
  const [input, setInput] = useState('');
  const bottomRef = useRef<HTMLDivElement>(null);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const isStreaming = messages.some((m) => m.streaming);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const autoResize = () => {
    const ta = textareaRef.current;
    if (ta) { ta.style.height = 'auto'; ta.style.height = `${Math.min(ta.scrollHeight, 120)}px`; }
  };

  const submit = () => {
    if (!input.trim() || isStreaming) return;
    onSend(input.trim());
    setInput('');
    if (textareaRef.current) textareaRef.current.style.height = 'auto';
  };

  const onKey = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submit(); }
  };

  const wsBadgeClass = {
    connected: 'ws-connected',
    connecting: 'ws-connecting',
    disconnected: 'ws-disconnected',
    error: 'ws-error',
  }[status];

  return (
    <section className="chat-panel">
      <div className="chat-header">
        <div className="chat-header-left">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--indigo)" strokeWidth="2">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
          <span className="chat-header-title">Research Chat</span>
          <span className={`chat-ws-badge ${wsBadgeClass}`}>
            <span style={{ width: 5, height: 5, borderRadius: '50%', background: 'currentColor', display: 'inline-block' }} />
            {status}
          </span>
        </div>
        {messages.length > 0 && (
          <button className="btn btn-ghost" style={{ padding: '4px 10px', fontSize: 11 }} onClick={onClear}>
            Clear
          </button>
        )}
      </div>

      <div className="chat-messages">
        {messages.length === 0 ? (
          <div className="chat-empty">
            <div className="chat-empty-icon">🤖</div>
            <h3>Ask anything</h3>
            <p>Ask questions about ingested papers — citations, methods, implementations, or comparisons.</p>
          </div>
        ) : (
          messages.map((msg) => <Message key={msg.id} msg={msg} />)
        )}
        <div ref={bottomRef} />
      </div>

      <div className="chat-input-wrap">
        <textarea
          ref={textareaRef}
          className="chat-textarea"
          rows={1}
          placeholder="Ask about papers, citations, or implementations…"
          value={input}
          onChange={(e) => { setInput(e.target.value); autoResize(); }}
          onKeyDown={onKey}
          disabled={isStreaming}
        />
        <button className="chat-send-btn" onClick={submit} disabled={!input.trim() || isStreaming} title="Send (Enter)">
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5">
            <path d="M22 2L11 13M22 2l-7 20-4-9-9-4 20-7z" />
          </svg>
        </button>
      </div>
    </section>
  );
}

function Message({ msg }: { msg: ChatMessage }) {
  const isUser = msg.role === 'user';
  const isEmpty = msg.streaming && msg.content === '';

  return (
    <div className={`chat-bubble-wrap ${msg.role}`}>
      <div className={`chat-bubble ${msg.role}`}>
        {isEmpty ? (
          <ThinkingDots />
        ) : (
          <>
            {msg.content}
            {msg.streaming && <span className="chat-cursor" />}
          </>
        )}
      </div>
      {!isUser && !msg.streaming && msg.sources && msg.sources.length > 0 && (
        <div className="chat-sources">
          {msg.sources.slice(0, 5).map((s) => (
            <span key={s} className="source-tag">{s.replace('arxiv:', '').replace('github:', '')}</span>
          ))}
        </div>
      )}
    </div>
  );
}

function ThinkingDots() {
  return (
    <span style={{ display: 'flex', gap: 4, alignItems: 'center', padding: '2px 0' }}>
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          style={{
            width: 6,
            height: 6,
            borderRadius: '50%',
            background: 'var(--indigo)',
            animation: `thinking 1.2s ease-in-out ${i * 0.2}s infinite`,
          }}
        />
      ))}
      <style>{`
        @keyframes thinking {
          0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
          30% { transform: translateY(-4px); opacity: 1; }
        }
      `}</style>
    </span>
  );
}
