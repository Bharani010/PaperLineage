import * as d3 from 'd3';
import { useEffect, useRef } from 'react';
import type { GraphLink, GraphNode } from '../types';

interface Props {
  nodes: GraphNode[];
  links: GraphLink[];
  selectedId: string | null;
  onNodeClick: (node: GraphNode) => void;
}

export function ForceGraph({ nodes, links, selectedId, onNodeClick }: Props) {
  const svgRef = useRef<SVGSVGElement>(null);
  const gRef = useRef<SVGGElement | null>(null);
  const simRef = useRef<d3.Simulation<GraphNode, GraphLink> | null>(null);

  useEffect(() => {
    const svg = d3.select(svgRef.current!);
    const { width, height } = svgRef.current!.getBoundingClientRect();

    svg.selectAll('*').remove();

    const defs = svg.append('defs');

    // ── Glow filters ─────────────────────────────────────────
    defs.append('filter').attr('id', 'glow-paper').html(`
      <feGaussianBlur stdDeviation="6" result="blur"/>
      <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
    `);
    defs.append('filter').attr('id', 'glow-repo').html(`
      <feGaussianBlur stdDeviation="5" result="blur"/>
      <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
    `);
    defs.append('filter').attr('id', 'glow-selected').html(`
      <feGaussianBlur stdDeviation="10" result="blur"/>
      <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
    `);
    defs.append('filter').attr('id', 'glow-hover').html(`
      <feGaussianBlur stdDeviation="14" result="blur"/>
      <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
    `);
    // text glow filters
    defs.append('filter').attr('id', 'text-glow').html(`
      <feGaussianBlur stdDeviation="2.5" result="blur"/>
      <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
    `);
    defs.append('filter').attr('id', 'text-glow-hover').html(`
      <feGaussianBlur stdDeviation="5" result="blur"/>
      <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
    `);

    // ── Glass gradients per node type ─────────────────────────
    const makeGlassGrad = (id: string, c1: string, c2: string) => {
      const g = defs.append('radialGradient').attr('id', id)
        .attr('cx', '38%').attr('cy', '30%').attr('r', '70%');
      g.append('stop').attr('offset', '0%').attr('stop-color', c1).attr('stop-opacity', '0.95');
      g.append('stop').attr('offset', '100%').attr('stop-color', c2).attr('stop-opacity', '0.98');
    };
    makeGlassGrad('glass-paper-root', '#2a3860', '#0e1428');
    makeGlassGrad('glass-paper',      '#1c2445', '#0b1020');
    makeGlassGrad('glass-repo-run',   '#103825', '#061810');
    makeGlassGrad('glass-repo-risky', '#382510', '#180e04');
    makeGlassGrad('glass-repo-skip',  '#2a1410', '#140804');

    // ── Arrow marker (tip-at-endpoint design) ─────────────────
    defs.append('marker')
      .attr('id', 'arrow')
      .attr('viewBox', '0 -5 10 10')
      .attr('refX', 8)
      .attr('refY', 0)
      .attr('markerWidth', 7)
      .attr('markerHeight', 7)
      .attr('orient', 'auto')
      .append('path')
      .attr('d', 'M0,-5L10,0L0,5Z')
      .attr('fill', 'rgba(139,181,232,0.7)');

    // ── Background ────────────────────────────────────────────
    const bg = defs.append('radialGradient').attr('id', 'bg-grad')
      .attr('cx', '50%').attr('cy', '50%').attr('r', '60%');
    bg.append('stop').attr('offset', '0%').attr('stop-color', '#0d0f18');
    bg.append('stop').attr('offset', '100%').attr('stop-color', '#080a0e');

    svg.append('rect').attr('width', width).attr('height', height).attr('fill', 'url(#bg-grad)');

    // ── Zoom container ────────────────────────────────────────
    const g = svg.append('g');
    gRef.current = g.node();

    const zoom = d3.zoom<SVGSVGElement, unknown>()
      .scaleExtent([0.12, 5])
      .on('zoom', (event) => g.attr('transform', event.transform));
    svg.call(zoom);

    if (nodes.length === 0) return;

    // ── Simulation ────────────────────────────────────────────
    const sim = d3.forceSimulation<GraphNode>(nodes)
      .force('link', d3.forceLink<GraphNode, GraphLink>(links)
        .id((d) => d.id)
        .distance((l) => {
          const src = l.source as GraphNode;
          const tgt = l.target as GraphNode;
          return src.isRoot || tgt.isRoot ? 140 : 100;
        })
        .strength(0.35))
      .force('charge', d3.forceManyBody().strength(-420))
      .force('center', d3.forceCenter(width / 2, height / 2))
      .force('collide', d3.forceCollide<GraphNode>((d) => nodeRadius(d) + 22));
    simRef.current = sim;

    // ── Links (paths so we can trim endpoints) ────────────────
    const link = g.append('g').selectAll<SVGPathElement, GraphLink>('path')
      .data(links)
      .join('path')
      .attr('fill', 'none')
      .attr('stroke', 'rgba(139,181,232,0.35)')
      .attr('stroke-width', 1.2)
      .attr('marker-end', 'url(#arrow)');

    // ── Node groups ───────────────────────────────────────────
    const node = g.append('g').selectAll<SVGGElement, GraphNode>('g')
      .data(nodes, (d) => d.id)
      .join('g')
      .attr('class', 'graph-node')
      .style('cursor', 'pointer')
      .call(
        d3.drag<SVGGElement, GraphNode>()
          .on('start', (event, d) => {
            if (!event.active) sim.alphaTarget(0.3).restart();
            d.fx = d.x; d.fy = d.y;
          })
          .on('drag', (event, d) => { d.fx = event.x; d.fy = event.y; })
          .on('end', (event, d) => {
            if (!event.active) sim.alphaTarget(0);
            d.fx = null; d.fy = null;
          }),
      );

    // outer selection ring
    node.append('circle')
      .attr('class', 'node-ring')
      .attr('r', (d) => nodeRadius(d) + 7)
      .attr('fill', 'none')
      .attr('stroke', (d) => d.type === 'paper' ? 'rgba(139,181,232,0.55)' : 'rgba(110,203,168,0.55)')
      .attr('stroke-width', 1.5)
      .style('opacity', (d) => (d.id === selectedId ? 1 : 0));

    // glass base circle
    node.append('circle')
      .attr('class', 'node-circle')
      .attr('r', (d) => nodeRadius(d))
      .attr('fill', (d) => glassGradId(d))
      .attr('stroke', (d) => d.type === 'paper' ? 'rgba(139,181,232,0.6)' : 'rgba(110,203,168,0.6)')
      .attr('stroke-width', (d) => d.isRoot ? 2 : 1.5)
      .attr('filter', (d) => d.id === selectedId ? 'url(#glow-selected)' : d.type === 'paper' ? 'url(#glow-paper)' : 'url(#glow-repo)');

    // glass shine (top-left highlight ellipse)
    node.append('ellipse')
      .attr('rx', (d) => nodeRadius(d) * 0.55)
      .attr('ry', (d) => nodeRadius(d) * 0.28)
      .attr('cx', (d) => -nodeRadius(d) * 0.18)
      .attr('cy', (d) => -nodeRadius(d) * 0.38)
      .attr('fill', 'rgba(255,255,255,0.12)')
      .attr('pointer-events', 'none');

    // pulse ring for root
    node.filter((d) => !!d.isRoot)
      .append('circle')
      .attr('r', (d) => nodeRadius(d) + 3)
      .attr('fill', 'none')
      .attr('stroke', 'rgba(139,181,232,0.45)')
      .attr('stroke-width', 1.5)
      .style('animation', 'pulse 2.5s ease-in-out infinite');

    // main label
    node.append('text')
      .attr('class', 'node-label-main')
      .attr('text-anchor', 'middle')
      .attr('dy', (d) => nodeRadius(d) + 17)
      .text((d) => truncate(d.label, d.isRoot ? 24 : 20))
      .attr('fill', (d) => d.id === selectedId ? '#e8e6e0' : '#b8b5b0')
      .attr('font-size', (d) => d.isRoot ? 13 : 11)
      .attr('font-weight', (d) => d.isRoot ? 600 : 400)
      .attr('font-family', "'DM Mono', monospace")
      .attr('filter', 'url(#text-glow)')
      .attr('pointer-events', 'none');

    // sub-label (year / score)
    node.append('text')
      .attr('class', 'node-label-sub')
      .attr('text-anchor', 'middle')
      .attr('dy', (d) => nodeRadius(d) + 30)
      .text((d) => {
        if (d.type === 'paper' && d.year) return String(d.year);
        if (d.type === 'repo' && d.runnabilityScore !== undefined) return `${d.runnabilityScore}/100`;
        return '';
      })
      .attr('fill', '#6a6865')
      .attr('font-size', 10)
      .attr('font-family', "'DM Mono', monospace")
      .attr('pointer-events', 'none');

    // ── Hover interactions ────────────────────────────────────
    node
      .on('mouseover', function(_, d) {
        d3.select(this).select('.node-circle')
          .attr('filter', 'url(#glow-hover)');
        d3.select(this).select('.node-label-main')
          .attr('font-size', d.isRoot ? 15 : 13)
          .attr('fill', '#e8e6e0')
          .attr('filter', 'url(#text-glow-hover)');
        d3.select(this).select('.node-label-sub')
          .attr('font-size', 11)
          .attr('fill', '#a0a0a0');
        // nudge sub-label down slightly to accommodate bigger main label
        d3.select(this).select('.node-label-sub')
          .attr('dy', nodeRadius(d) + 33);
      })
      .on('mouseout', function(_, d) {
        const isSel = d.id === selectedId;
        d3.select(this).select('.node-circle')
          .attr('filter', isSel ? 'url(#glow-selected)' : d.type === 'paper' ? 'url(#glow-paper)' : 'url(#glow-repo)');
        d3.select(this).select('.node-label-main')
          .attr('font-size', d.isRoot ? 13 : 11)
          .attr('fill', isSel ? '#e8e6e0' : '#b8b5b0')
          .attr('filter', 'url(#text-glow)');
        d3.select(this).select('.node-label-sub')
          .attr('font-size', 10)
          .attr('fill', '#6a6865')
          .attr('dy', nodeRadius(d) + 30);
      })
      .on('click', (_, d) => onNodeClick(d));

    // ── Tick: trim link endpoints to node surfaces ────────────
    sim.on('tick', () => {
      link.attr('d', (l) => {
        const s = l.source as GraphNode;
        const t = l.target as GraphNode;
        const sx = s.x ?? 0, sy = s.y ?? 0;
        const tx = t.x ?? 0, ty = t.y ?? 0;
        const dx = tx - sx, dy = ty - sy;
        const dist = Math.sqrt(dx * dx + dy * dy) || 1;
        const rs = nodeRadius(s) + 2;
        const rt = nodeRadius(t) + 4; // leave room for arrowhead
        const x1 = sx + (dx / dist) * rs;
        const y1 = sy + (dy / dist) * rs;
        const x2 = tx - (dx / dist) * rt;
        const y2 = ty - (dy / dist) * rt;
        return `M${x1},${y1}L${x2},${y2}`;
      });

      node.attr('transform', (d) => `translate(${d.x ?? 0},${d.y ?? 0})`);
    });

    return () => { sim.stop(); };
  }, [nodes, links, selectedId, onNodeClick]);

  // ── Update selection ring without full re-render ──────────
  useEffect(() => {
    if (!gRef.current) return;
    const root = d3.select(gRef.current);
    root.selectAll<SVGCircleElement, GraphNode>('.node-ring')
      .style('opacity', (d) => (d.id === selectedId ? 1 : 0));
    root.selectAll<SVGCircleElement, GraphNode>('.node-circle')
      .attr('filter', (d) => d.id === selectedId ? 'url(#glow-selected)' : d.type === 'paper' ? 'url(#glow-paper)' : 'url(#glow-repo)');
    root.selectAll<SVGTextElement, GraphNode>('.node-label-main')
      .attr('fill', (d) => d.id === selectedId ? '#e8e6e0' : '#b8b5b0');
  }, [selectedId]);

  return (
    <div className="center-panel">
      <svg ref={svgRef} className="graph-svg" />

      {nodes.length === 0 && (
        <div className="graph-empty">
          <div className="graph-empty-orb" />
          <h2>No papers traced yet</h2>
          <p>Enter an arXiv ID above to map a paper&apos;s citation lineage and discover GitHub implementations.</p>
        </div>
      )}

      {nodes.length > 0 && (
        <div className="graph-legend">
          <div className="legend-item">
            <div className="legend-dot paper" />
            <span>Paper</span>
          </div>
          <div className="legend-item">
            <div className="legend-dot repo" />
            <span>Repository</span>
          </div>
        </div>
      )}
    </div>
  );
}

function nodeRadius(d: GraphNode): number {
  if (d.type === 'paper') return d.isRoot ? 28 : 20;
  const stars = d.stars ?? 0;
  return Math.max(11, Math.min(20, 9 + Math.log10(stars + 1) * 4));
}

function glassGradId(d: GraphNode): string {
  if (d.type === 'paper') return d.isRoot ? 'url(#glass-paper-root)' : 'url(#glass-paper)';
  const score = d.runnabilityScore ?? 0;
  if (score >= 71) return 'url(#glass-repo-run)';
  if (score >= 41) return 'url(#glass-repo-risky)';
  return 'url(#glass-repo-skip)';
}

function truncate(s: string, max: number): string {
  return s.length > max ? s.slice(0, max - 1) + '…' : s;
}
