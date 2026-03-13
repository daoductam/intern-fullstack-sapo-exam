import { useState, useEffect, useCallback } from "react";

// Dynamically generated User ID for testing purposes (since there is no auth context)
const CURRENT_USER_ID = Math.floor(Math.random() * 10000) + 1;

function formatPrice(price) {
  return new Intl.NumberFormat("vi-VN", {
    style: "currency",
    currency: "VND",
  }).format(price);
}

function useCountdown(targetMs) {
  const [timeLeft, setTimeLeft] = useState(targetMs);
  useEffect(() => {
    const timer = setInterval(() => {
      setTimeLeft((t) => Math.max(0, t - 1000));
    }, 1000);
    return () => clearInterval(timer);
  }, []);
  const minutes = Math.floor(timeLeft / 60000);
  const seconds = Math.floor((timeLeft % 60000) / 1000);
  return { minutes, seconds, expired: timeLeft === 0 };
}

function Toast({ toasts }) {
  return (
    <div className="toast-container">
      {toasts.map((t) => (
        <div key={t.id} className={`toast toast-${t.type}`}>
          <span className="toast-icon">{t.type === "success" ? "✓" : "✕"}</span>
          <span>{t.message}</span>
        </div>
      ))}
    </div>
  );
}

function ProductCard({ product, onBuy, purchasing }) {
  const discountPct = Math.round(
    ((product.originalPrice - product.salePrice) / product.originalPrice) * 100
  );
  const stockPct = Math.min(100, (product.stock / 100) * 100);
  const outOfStock = product.stock === 0;

  return (
    <div className={`product-card ${outOfStock ? "out-of-stock" : ""}`}>
      <div className="product-image">{product.image}</div>

      <div className="product-info">
        <h3 className="product-name">{product.name}</h3>

        <div className="price-row">
          <span className="sale-price">{formatPrice(product.salePrice)}</span>
          <span className="original-price">
            {formatPrice(product.originalPrice)}
          </span>
          <span className="discount-badge">-{discountPct}%</span>
        </div>

        <div className="stock-section">
          <div className="stock-bar">
            <div
              className="stock-fill"
              style={{
                width: `${stockPct}%`,
                background: stockPct < 20 ? "#ef4444" : "#f97316",
              }}
            />
          </div>
          <span className="stock-text">
            {outOfStock ? "Hết hàng" : `Còn ${product.stock} sản phẩm`}
          </span>
        </div>

        <button
          className={`buy-btn ${outOfStock ? "btn-disabled" : ""} ${
            purchasing === product.id ? "btn-loading" : ""
          }`}
          onClick={() => !outOfStock && onBuy(product)}
          disabled={outOfStock || purchasing === product.id}
        >
          {purchasing === product.id ? (
            <>
              <span className="spinner" /> Đang xử lý...
            </>
          ) : outOfStock ? (
            "Hết hàng"
          ) : (
            "Mua ngay ⚡"
          )}
        </button>
      </div>
    </div>
  );
}

export default function FlashSalePage() {
  const [products, setProducts] = useState([]);
  const [purchasing, setPurchasing] = useState(null);
  const [toasts, setToasts] = useState([]);
  const { minutes, seconds, expired } = useCountdown(30 * 60 * 1000); // 30 min

  useEffect(() => {
    fetch("http://localhost:8080/api/flash-sale/products")
      .then(res => res.json())
      .then(data => {
        // Original prices are not in the DB model according to the provided sample, so let's mock original price based on 50% discount
        const enrichedData = data.map(p => ({
          ...p,
          originalPrice: p.salePrice * 2
        }));
        setProducts(enrichedData);
      })
      .catch(err => console.error("Failed to load products:", err));
  }, []);

  const addToast = useCallback((message, type) => {
    const id = Date.now();
    setToasts((prev) => [...prev, { id, message, type }]);
    setTimeout(() => setToasts((prev) => prev.filter((t) => t.id !== id)), 4000);
  }, []);

  const handleBuy = useCallback(
    async (product) => {
      if (expired) {
        addToast("Flash Sale đã kết thúc!", "error");
        return;
      }
      setPurchasing(product.id);

      try {
        const response = await fetch("http://localhost:8080/api/flash-sale/order", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            productId: product.id,
            userId: CURRENT_USER_ID,
            quantity: 1,
          }),
        });

        const data = await response.json();

        if (data.success) {
          addToast(data.message, "success");
          // Optimistically update stock in UI
          setProducts((prev) =>
            prev.map((p) =>
              p.id === product.id ? { ...p, stock: Math.max(0, p.stock - 1) } : p
            )
          );
        } else {
          // Map error codes to user-friendly messages
          const errorMessages = {
            OUT_OF_STOCK: "Sản phẩm vừa hết hàng. Bạn đến hơi muộn!",
            EXCEED_USER_LIMIT: "Bạn đã mua đủ số lượng tối đa (2 sản phẩm).",
            INVALID_QUANTITY: "Số lượng không hợp lệ.",
            SYSTEM_ERROR: "Hệ thống đang bận, vui lòng thử lại sau.",
          };
          addToast(
            errorMessages[data.errorCode] || data.message,
            "error"
          );
        }
      } catch (err) {
        addToast("Không thể kết nối server. Kiểm tra mạng của bạn.", "error");
      } finally {
        setPurchasing(null);
      }
    },
    [expired, addToast]
  );

  return (
    <>
      <style>{`
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: 'Be Vietnam Pro', system-ui, sans-serif; background: #0f0f0f; color: #f5f5f5; }

        .page { max-width: 1100px; margin: 0 auto; padding: 32px 16px; }

        .header { text-align: center; margin-bottom: 40px; }
        .flash-badge {
          display: inline-flex; align-items: center; gap: 8px;
          background: #f97316; color: #fff; font-weight: 700;
          font-size: 12px; letter-spacing: 0.1em; text-transform: uppercase;
          padding: 4px 14px; border-radius: 100px; margin-bottom: 12px;
        }
        .header h1 { font-size: 42px; font-weight: 800; letter-spacing: -1px; }
        .header h1 span { color: #f97316; }
        .countdown {
          display: inline-flex; align-items: center; gap: 6px;
          background: #1a1a1a; border: 1px solid #2a2a2a;
          border-radius: 12px; padding: 10px 20px; margin-top: 16px;
          font-size: 14px; color: #888;
        }
        .countdown-time {
          font-size: 22px; font-weight: 700; font-variant-numeric: tabular-nums;
          color: ${expired ? "#ef4444" : "#f97316"};
        }

        .products-grid {
          display: grid;
          grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
          gap: 20px;
        }

        .product-card {
          background: #1a1a1a; border: 1px solid #2a2a2a;
          border-radius: 16px; padding: 20px; transition: all .2s;
          display: flex; flex-direction: column; gap: 16px;
        }
        .product-card:hover { border-color: #f97316; transform: translateY(-2px); }
        .product-card.out-of-stock { opacity: 0.55; }

        .product-image { font-size: 48px; text-align: center; }
        .product-info { display: flex; flex-direction: column; gap: 12px; flex: 1; }
        .product-name { font-size: 15px; font-weight: 600; line-height: 1.4; }

        .price-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
        .sale-price { font-size: 20px; font-weight: 800; color: #f97316; }
        .original-price { font-size: 13px; color: #555; text-decoration: line-through; }
        .discount-badge {
          background: #f97316; color: #fff; font-size: 11px; font-weight: 700;
          padding: 2px 8px; border-radius: 100px;
        }

        .stock-section { display: flex; flex-direction: column; gap: 6px; }
        .stock-bar { height: 4px; background: #2a2a2a; border-radius: 100px; overflow: hidden; }
        .stock-fill { height: 100%; border-radius: 100px; transition: width .3s; }
        .stock-text { font-size: 12px; color: #666; }

        .buy-btn {
          width: 100%; padding: 12px; border: none; border-radius: 10px;
          font-size: 15px; font-weight: 700; cursor: pointer;
          background: linear-gradient(135deg, #f97316, #ea580c);
          color: #fff; transition: all .15s;
          display: flex; align-items: center; justify-content: center; gap: 8px;
        }
        .buy-btn:hover:not(.btn-disabled):not(.btn-loading) {
          background: linear-gradient(135deg, #fb923c, #f97316);
          transform: scale(1.02);
        }
        .btn-disabled { background: #2a2a2a !important; color: #555 !important; cursor: not-allowed; }
        .btn-loading { opacity: .8; cursor: wait; }

        .spinner {
          width: 14px; height: 14px; border: 2px solid rgba(255,255,255,.3);
          border-top-color: #fff; border-radius: 50%;
          animation: spin .7s linear infinite;
        }
        @keyframes spin { to { transform: rotate(360deg); } }

        .toast-container {
          position: fixed; bottom: 24px; right: 24px;
          display: flex; flex-direction: column; gap: 10px; z-index: 1000;
        }
        .toast {
          display: flex; align-items: center; gap: 10px;
          padding: 12px 18px; border-radius: 10px;
          font-size: 14px; font-weight: 500; min-width: 260px;
          animation: slideIn .25s ease; box-shadow: 0 4px 20px rgba(0,0,0,.4);
        }
        .toast-success { background: #14532d; color: #86efac; border: 1px solid #166534; }
        .toast-error { background: #450a0a; color: #fca5a5; border: 1px solid #7f1d1d; }
        .toast-icon { font-weight: 700; font-size: 16px; }
        @keyframes slideIn { from { opacity: 0; transform: translateX(20px); } to { opacity: 1; transform: none; } }

        .limit-note {
          text-align: center; color: #555; font-size: 13px; margin-top: 32px;
        }
      `}</style>

      <div className="page">
        <div className="header">
          <div className="flash-badge">⚡ Flash Sale</div>
          <h1>Ưu đãi <span>50%</span> có thời hạn</h1>
          <div className="countdown">
            <span>Kết thúc sau:</span>
            <span className="countdown-time">
              {expired
                ? "Đã kết thúc"
                : `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`}
            </span>
          </div>
        </div>

        <div className="products-grid">
          {products.map((product) => (
            <ProductCard
              key={product.id}
              product={product}
              onBuy={handleBuy}
              purchasing={purchasing}
            />
          ))}
        </div>

        <p className="limit-note">
          Mỗi khách hàng tối đa 2 sản phẩm · Số lượng có hạn · Không hoàn trả
        </p>
      </div>

      <Toast toasts={toasts} />
    </>
  );
}
