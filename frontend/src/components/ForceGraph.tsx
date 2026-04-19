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

    // ── Defs: filters + arrow marker ────────────────────────
    const defs = svg.append('defs');

    defs.append('filter').attr('id', 'glow-paper').html(`
      <feGaussianBlur stdDeviation="5" result="blur"/>
      <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
    `);

    defs.append('filter').attr('id', 'glow-repo').html(`
      <feGaussianBlur stdDeviation="4" result="blur"/>
      <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
    `);

    defs.append('filter').attr('id', 'glow-selected').html(`
      <feGaussianBlur stdDeviation="8" result="blur"/>
      <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
    `);

    defs.append('marker')
      .attr('id', 'arrow')
      .attr('viewBox', '0 -4 8 8')
      .attr('refX', 14)
      .attr('refY', 0)
      .attr('markerWidth', 6)
      .attr('markerHeight', 6)
      .attr('orient', 'auto')
      .append('path')
      .attr('d', 'M0,-4L8,0L0,4')
      .attr('fill', 'rgba(99,102,241,0.4)');

    // ── Radial background gradient ───────────────────────────
    const grad = defs.append('radialGradient')
      .attr('id', 'bg-grad')
      .attr('cx', '50%').attr('cy', '50%').attr('r', '60%');
    grad.append('stop').attr('offset', '0%').attr('stop-color', '#0d0d24');
    grad.append('stop').attr('offset', '100%').attr('stop-color', '#07070f');

    svg.append('rect').attr('width', width).attr('height', height)
      .attr('fill', 'url(#bg-grad)');

    // ── Zoom container ───────────────────────────────────────
    const g = svg.append('g');
    gRef.current = g.node();

    const zoom = d3.zoom<SVGSVGElement, unknown>()
      .scaleExtent([0.15, 4])
      .on('zoom', (event) => g.attr('transform', event.transform));
    svg.call(zoom);

    if (nodes.length === 0) return;

    // ── Build simulation ─────────────────────────────────────
    const sim = d3.forceSimulation<GraphNode>(nodes)
      .force('link', d3.forceLink<GraphNode, GraphLink>(links)
        .id((d) => d.id)
        .distance((l) => {
          const src = l.source as GraphNode;
          const tgt = l.target as GraphNode;
          return src.isRoot || tgt.isRoot ? 120 : 90;
        })
        .strength(0.4))
      .force('charge', d3.forceManyBody().strength(-350))
      .force('center', d3.forceCenter(width / 2, height / 2))
      .force('collide', d3.forceCollide<GraphNode>((d) => nodeRadius(d) + 18));
    simRef.current = sim;

    // ── Links ────────────────────────────────────────────────
    const link = g.append('g').selectAll('line')
      .data(links)
      .join('line')
      .attr('class', 'graph-link')
      .attr('marker-end', 'url(#arrow)');

    // ── Nodes ────────────────────────────────────────────────
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

    // outer glow ring (selected indicator)
    node.append('circle')
      .attr('r', (d) => nodeRadius(d) + 6)
      .attr('fill', 'none')
      .attr('stroke', (d) => d.type === 'paper' ? 'rgba(99,102,241,0.5)' : 'rgba(16,185,129,0.5)')
      .attr('stroke-width', 1.5)
      .attr('class', 'node-ring')
      .style('opacity', (d) => (d.id === selectedId ? 1 : 0));

    // main circle
    node.append('circle')
      .attr('class', 'node-circle')
      .attr('r', (d) => nodeRadius(d))
      .attr('fill', (d) => nodeFill(d))
      .attr('stroke', (d) => d.type === 'paper' ? '#6366f1' : '#10b981')
      .attr('stroke-width', (d) => d.isRoot ? 2.5 : 1.5)
      .attr('filter', (d) => {
        if (d.id === selectedId) return 'url(#glow-selected)';
        return d.type === 'paper' ? 'url(#glow-paper)' : 'url(#glow-repo)';
      });

    // pulse ring for root paper
    node.filter((d) => d.isRoot === true)
      .append('circle')
      .attr('r', (d) => nodeRadius(d) + 2)
      .attr('fill', 'none')
      .attr('stroke', 'rgba(99,102,241,0.6)')
      .attr('stroke-width', 1)
      .style('animation', 'pulse 2.5s ease-in-out infinite');

    // label
    node.append('text')
      .attr('class', 'node-label')
      .attr('dy', (d) => nodeRadius(d) + 14)
      .text((d) => truncate(d.label, d.isRoot ? 22 : 18))
      .attr('fill', (d) => d.id === selectedId ? '#f1f5f9' : '#94a3b8')
      .attr('font-size', (d) => d.isRoot ? 12 : 10)
      .attr('font-weight', (d) => d.isRoot ? 600 : 400);

    // sublabel (year for papers, stars for repos)
    node.append('text')
      .attr('class', 'node-label')
      .attr('dy', (d) => nodeRadius(d) + 26)
      .text((d) => {
        if (d.type === 'paper' && d.year) return String(d.year);
        if (d.type === 'repo' && d.stars) return `★ ${d.stars.toLocaleString()}`;
        return '';
      })
      .attr('fill', '#475569')
      .attr('font-size', 9);

    node.on('click', (_, d) => onNodeClick(d));

    // ── Tick ─────────────────────────────────────────────────
    sim.on('tick', () => {
      link
        .attr('x1', (d) => (d.source as GraphNode).x ?? 0)
        .attr('y1', (d) => (d.source as GraphNode).y ?? 0)
        .attr('x2', (d) => (d.target as GraphNode).x ?? 0)
        .attr('y2', (d) => (d.target as GraphNode).y ?? 0);

      node.attr('transform', (d) => `translate(${d.x ?? 0},${d.y ?? 0})`);
    });

    return () => { sim.stop(); };
  }, [nodes, links, selectedId, onNodeClick]);

  // update selected ring when selection changes without full re-render
  useEffect(() => {
    if (!gRef.current) return;
    d3.select(gRef.current).selectAll<SVGCircleElement, GraphNode>('.node-ring')
      .style('opacity', (d) => (d.id === selectedId ? 1 : 0));
    d3.select(gRef.current).selectAll<SVGTextElement, GraphNode>('.node-label:first-of-type')
      .attr('fill', (d) => (d.id === selectedId ? '#f1f5f9' : '#94a3b8'));
  }, [selectedId]);

  return (
    <div className="graph-panel">
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
  if (d.type === 'paper') return d.isRoot ? 26 : 18;
  const stars = d.stars ?? 0;
  return Math.max(10, Math.min(18, 8 + Math.log10(stars + 1) * 4));
}

function nodeFill(d: GraphNode): string {
  if (d.type === 'paper') {
    return d.isRoot
      ? 'radial-gradient(circle at 35% 35%, #a5b4fc, #4f46e5)' // fallback
      : '#3730a3';
  }
  return '#065f46';
}

function truncate(s: string, max: number): string {
  return s.length > max ? s.slice(0, max - 1) + '…' : s;
}
